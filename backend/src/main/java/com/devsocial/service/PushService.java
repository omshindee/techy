package com.devsocial.service;

import com.devsocial.dto.PushDtos.SubscribeRequest;
import com.devsocial.exception.ApiException;
import com.devsocial.model.PushSubscription;
import com.devsocial.model.User;
import com.devsocial.push.WebPushCrypto;
import com.devsocial.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phone/desktop notifications that arrive while Techy is closed (Web Push).
 * <p>
 * Needs a VAPID key pair (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY); without one, push is simply off.
 * Pushes are sent after the surrounding transaction commits, on a small background pool, so a slow
 * push service never delays a request and a rolled-back action never notifies anyone. Devices whose
 * subscription has expired (404/410 from the push service) are forgotten.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_BODY = 240;

    /** What a notification shows. {@code tag} groups notifications (a newer one replaces an older one). */
    public record Push(String title, String body, String url, String tag, boolean urgent) {}

    private final PushSubscriptionRepository subscriptions;
    private final UserService userService;
    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });
    /** VAPID tokens per push service origin, reused until close to expiry. */
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    private record Token(String header, long expiresAt) {}

    public PushService(PushSubscriptionRepository subscriptions, UserService userService,
                       @Value("${app.push.vapid.public-key:}") String publicKey,
                       @Value("${app.push.vapid.private-key:}") String privateKey,
                       @Value("${app.push.vapid.subject:}") String subject) {
        this.subscriptions = subscriptions;
        this.userService = userService;
        this.subject = subject == null ? "" : subject.strip();
        String pub = publicKey == null ? "" : publicKey.strip();
        String priv = privateKey == null ? "" : privateKey.strip();
        ECPrivateKey key = null;
        if (!pub.isEmpty() && !priv.isEmpty()) {
            try {
                key = WebPushCrypto.privateKey(WebPushCrypto.b64(priv));
                WebPushCrypto.publicKey(WebPushCrypto.b64(pub)); // validate
            } catch (Exception e) {
                log.error("VAPID keys are invalid, so push notifications are off: {}", e.getMessage());
                key = null;
            }
        }
        this.privateKey = key;
        this.publicKey = key == null ? null : pub;
        if (key == null) log.info("Push notifications are off (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY not set).");
    }

    public boolean enabled() {
        return privateKey != null;
    }

    /** The key browsers need to subscribe, or null when push is off. */
    public String publicKey() {
        return publicKey;
    }

    // --- subscriptions -------------------------------------------------------------------------

    @Transactional
    public void subscribe(String username, SubscribeRequest req) {
        if (!enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Push notifications aren't set up on this server.");
        URI uri;
        try {
            uri = URI.create(req.endpoint().strip());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid push endpoint");
        }
        // Only real push services: https URLs. Stops the server being used to call arbitrary/internal URLs.
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid push endpoint");
        }
        try {
            if (WebPushCrypto.b64(req.keys().auth()).length != 16) throw new IllegalArgumentException();
            WebPushCrypto.publicKey(WebPushCrypto.b64(req.keys().p256dh()));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid push keys");
        }
        User user = userService.getEntity(username);
        PushSubscription s = subscriptions.findByEndpoint(uri.toString()).orElseGet(PushSubscription::new);
        s.setUser(user); // a shared device notifies whoever subscribed on it last
        s.setEndpoint(uri.toString());
        s.setP256dh(req.keys().p256dh().strip());
        s.setAuth(req.keys().auth().strip());
        subscriptions.save(s);
    }

    @Transactional
    public void unsubscribe(String username, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint.strip(), userService.getEntity(username).getId());
    }

    // --- sending -------------------------------------------------------------------------------

    /** Notifies every device of {@code recipient}, once the current transaction (if any) has committed. */
    public void notify(User recipient, Push push) {
        if (!enabled() || recipient.isSuspended()) return;
        Long userId = recipient.getId();
        Runnable send = () -> pool.execute(() -> sendToUser(userId, push));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendToUser(Long userId, Push push) {
        try {
            List<PushSubscription> devices = subscriptions.findByUserId(userId);
            if (devices.isEmpty()) return;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", push.title());
            payload.put("body", trim(push.body()));
            payload.put("url", push.url());
            payload.put("tag", push.tag());
            byte[] plaintext = json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            for (PushSubscription d : devices) sendToDevice(d, plaintext, push.urgent());
        } catch (Exception e) {
            log.warn("Push to user {} failed: {}", userId, e.toString());
        }
    }

    private void sendToDevice(PushSubscription device, byte[] plaintext, boolean urgent) {
        try {
            URI endpoint = URI.create(device.getEndpoint());
            byte[] body = WebPushCrypto.encrypt(plaintext, WebPushCrypto.b64(device.getP256dh()), WebPushCrypto.b64(device.getAuth()));
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", vapid(endpoint))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(Duration.ofDays(1).toSeconds()))
                    .header("Urgency", urgent ? "high" : "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int status = res.statusCode();
            if (status == 404 || status == 410) {
                subscriptions.deleteByEndpoint(device.getEndpoint()); // expired or unsubscribed
            } else if (status / 100 != 2) {
                log.warn("Push service {} answered HTTP {}: {}", endpoint.getHost(), status, res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Push to a device failed: {}", e.toString());
        }
    }

    private String vapid(URI endpoint) throws Exception {
        String audience = endpoint.getScheme() + "://" + endpoint.getAuthority();
        long now = Instant.now().getEpochSecond();
        Token t = tokens.get(audience);
        if (t == null || t.expiresAt() - now < 3600) {
            long exp = now + 12 * 3600; // push services accept at most 24 h
            t = new Token(WebPushCrypto.vapidAuthorization(audience, subject, exp, privateKey, WebPushCrypto.b64(publicKey)), exp);
            tokens.put(audience, t);
        }
        return t.header();
    }

    private static String trim(String s) {
        if (s == null) return "";
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() <= MAX_BODY ? t : t.substring(0, MAX_BODY - 1) + "…";
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
