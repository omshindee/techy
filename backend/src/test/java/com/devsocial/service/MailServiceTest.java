package com.devsocial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Picks the right way to send, and talks to Brevo's API in the shape it expects. */
class MailServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> noSmtp = mock(ObjectProvider.class);

    @Test
    void parsesTheFromAddress() {
        assertArrayEquals(new String[]{"Techy", "hello@example.com"}, MailService.parseFrom("Techy <hello@example.com>"));
        assertArrayEquals(new String[]{"Techy Team", "a@b.co"}, MailService.parseFrom("\"Techy Team\" < a@b.co >"));
        assertArrayEquals(new String[]{"Techy", "plain@example.com"}, MailService.parseFrom("plain@example.com"));
    }

    @Test
    void choosesBrevoWhenAKeyIsSetOtherwiseLogs() {
        assertEquals("log", new MailService(noSmtp, "", "Techy <a@b.co>", "http://x", "", "http://unused").provider());
        assertEquals("brevo", new MailService(noSmtp, "smtp.example.com", "Techy <a@b.co>", "http://x", " key ", "http://unused").provider());
    }

    @Test
    void sendsThroughBrevosApi() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", ex -> {
            key.set(ex.getRequestHeaders().getFirst("api-key"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"messageId\":\"<1@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(201, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
            MailService mail = new MailService(noSmtp, "", "Techy <hello@example.com>", "http://x", "test-key", url);
            mail.sendWithBrevo("someone@example.com", "Confirm your email for Techy", "Open this link: http://x/verify");

            assertEquals("test-key", key.get());
            JsonNode sent = new ObjectMapper().readTree(body.get());
            assertEquals("hello@example.com", sent.at("/sender/email").asText());
            assertEquals("Techy", sent.at("/sender/name").asText());
            assertEquals("someone@example.com", sent.at("/to/0/email").asText());
            assertEquals("Confirm your email for Techy", sent.get("subject").asText());
            assertTrue(sent.get("textContent").asText().contains("http://x/verify"));
        } finally {
            server.stop(0);
        }
    }
}
