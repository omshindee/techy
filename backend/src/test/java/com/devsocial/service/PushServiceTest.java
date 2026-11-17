package com.devsocial.service;

import com.devsocial.model.PushSubscription;
import com.devsocial.model.User;
import com.devsocial.push.WebPushCrypto;
import com.devsocial.repository.PushSubscriptionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Sends to each device of a user with the Web Push headers, and forgets devices that are gone. */
class PushServiceTest {

    @Test
    void sendsEncryptedPushesAndForgetsExpiredDevices() throws Exception {
        KeyPair vapid = WebPushCrypto.generateKeyPair();
        String pub = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) vapid.getPublic()));
        String priv = WebPushCrypto.b64(WebPushCrypto.encode((ECPrivateKey) vapid.getPrivate()));

        Map<String, Map<String, String>> seen = new ConcurrentHashMap<>();
        AtomicInteger bodyLength = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            seen.put(path, Map.of(
                    "auth", ex.getRequestHeaders().getFirst("Authorization"),
                    "encoding", ex.getRequestHeaders().getFirst("Content-Encoding"),
                    "ttl", ex.getRequestHeaders().getFirst("TTL"),
                    "urgency", ex.getRequestHeaders().getFirst("Urgency")));
            bodyLength.set(ex.getRequestBody().readAllBytes().length);
            ex.sendResponseHeaders(path.equals("/gone") ? 410 : 201, -1);
            ex.close();
            done.countDown();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            User alice = new User();
            alice.setId(7L);
            KeyPair browser = WebPushCrypto.generateKeyPair();
            String p256dh = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) browser.getPublic()));
            String auth = WebPushCrypto.b64(new byte[16]);

            PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
            when(repo.findByUserId(7L)).thenReturn(List.of(device(base + "/phone", p256dh, auth), device(base + "/gone", p256dh, auth)));
            PushService push = new PushService(repo, mock(UserService.class), pub, priv, "https://techy.example");
            assertTrue(push.enabled());

            push.notify(alice, new PushService.Push("Bob", "hey there", "/messages/bob", "dm-bob", true));
            assertTrue(done.await(10, TimeUnit.SECONDS));

            Map<String, String> phone = seen.get("/phone");
            assertEquals("aes128gcm", phone.get("encoding"));
            assertEquals("high", phone.get("urgency"));
            assertEquals("86400", phone.get("ttl"));
            assertTrue(phone.get("auth").startsWith("vapid t=") && phone.get("auth").endsWith(", k=" + pub));
            assertTrue(bodyLength.get() > 86 + 16); // header + ciphertext + tag
            // The 410 device is removed; the working one isn't.
            verify(repo, timeout(5000)).deleteByEndpoint(base + "/gone");
            verify(repo, never()).deleteByEndpoint(base + "/phone");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void offWithoutKeys() {
        PushService push = new PushService(mock(PushSubscriptionRepository.class), mock(UserService.class), "", "", "");
        assertFalse(push.enabled());
        assertNull(push.publicKey());
    }

    private static PushSubscription device(String endpoint, String p256dh, String auth) {
        PushSubscription s = new PushSubscription();
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        return s;
    }
}
