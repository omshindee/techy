package com.devsocial.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends account emails (verification, password reset), using the first of these that is set up:
 * <ol>
 *   <li>Brevo's HTTPS API (BREVO_API_KEY). Works on hosts that block outgoing SMTP ports, such as
 *       free cloud plans.</li>
 *   <li>SMTP (SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD).</li>
 *   <li>Neither (local development): the email is written to the log instead, so you can copy the
 *       link from `docker compose logs backend`.</li>
 * </ol>
 * Sending runs in the background so a slow mail server never delays a request, and response times
 * don't reveal whether an account exists.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Pattern NAME_AND_ADDRESS = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>\\s]+)\\s*>\\s*$");

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String baseUrl;
    private final String brevoApiKey;
    private final String brevoUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.base-url}") String baseUrl,
                       @Value("${app.mail.brevo.api-key:}") String brevoApiKey,
                       @Value("${app.mail.brevo.url:https://api.brevo.com/v3/smtp/email}") String brevoUrl) {
        this.sender = sender;
        this.host = host;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.strip();
        this.brevoUrl = brevoUrl;
    }

    /** Absolute link into the web app, e.g. link("/verify-email?token=..."). */
    public String link(String pathAndQuery) {
        return baseUrl + pathAndQuery;
    }

    /** Which way emails go out: "brevo", "smtp" or "log". */
    public String provider() {
        if (!brevoApiKey.isEmpty()) return "brevo";
        if (host != null && !host.isBlank() && sender.getIfAvailable() != null) return "smtp";
        return "log";
    }

    @Async
    public void send(String to, String subject, String body) {
        switch (provider()) {
            case "brevo" -> sendWithBrevo(to, subject, body);
            case "smtp" -> sendWithSmtp(to, subject, body);
            default -> log.warn("Email sending is not configured, so this email was NOT sent. To: {} | Subject: {}\n{}",
                    to, subject, body);
        }
    }

    private void sendWithSmtp(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.getObject().send(msg);
        } catch (MailException e) {
            log.error("Failed to send email '{}' to {}", subject, to, e);
        }
    }

    /** POST https://api.brevo.com/v3/smtp/email (Brevo "transactional email"). */
    void sendWithBrevo(String to, String subject, String body) {
        try {
            String[] sender = parseFrom(from);
            String payload = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", sender[0], "email", sender[1]),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", body));
            HttpRequest req = HttpRequest.newBuilder(URI.create(brevoUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.error("Brevo refused email '{}' to {}: HTTP {} {}", subject, to, res.statusCode(), res.body());
            } else {
                log.info("Sent email '{}' to {} via Brevo", subject, to);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending email '{}' to {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send email '{}' to {} via Brevo", subject, to, e);
        }
    }

    /** "Techy <hello@example.com>" → ["Techy", "hello@example.com"]; a bare address gets the name "Techy". */
    static String[] parseFrom(String from) {
        Matcher m = NAME_AND_ADDRESS.matcher(from == null ? "" : from);
        if (m.matches()) {
            String name = m.group(1).replaceAll("^\"|\"$", "").strip();
            return new String[]{name.isEmpty() ? "Techy" : name, m.group(2)};
        }
        return new String[]{"Techy", from == null ? "" : from.strip()};
    }
}
