package com.devsocial.controller;

import com.devsocial.service.MailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Email confirmation, password reset, honeypot, rate limits and moderator promotion, with the real switches on. */
@SpringBootTest(properties = {
        "app.auth.require-verified-email=true",
        "app.ratelimit.enabled=true",
        "app.security.trust-proxy-headers=true",
        "app.admin.emails=boss@example.com"})
@AutoConfigureMockMvc
class AccountFlowIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean MailService mail;

    @BeforeEach
    void links() {
        when(mail.link(anyString())).thenAnswer(i -> "http://techy.test" + i.getArgument(0));
    }

    /** Each test gets its own IP so the per-IP limits don't interfere. */
    private static String ip() {
        return "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private String register(String name, String email, String ip) throws Exception {
        String body = om.writeValueAsString(Map.of("username", name, "email", email, "password", "password123"));
        String res = mvc.perform(post("/api/auth/register").header("X-Real-IP", ip).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    /** The token from the latest email sent to {@code email} whose subject contains {@code subject}. */
    private String tokenFromEmail(String email, String subject) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), org.mockito.ArgumentMatchers.contains(subject), body.capture());
        Matcher m = TOKEN.matcher(body.getValue());
        if (!m.find()) throw new AssertionError("no link in email: " + body.getValue());
        return m.group(1);
    }

    private String postJson(String text) throws Exception {
        return om.writeValueAsString(Map.of("content", text, "category", "Java"));
    }

    @Test
    void newAccountsMustConfirmTheirEmailBeforePosting() throws Exception {
        String name = "v_" + UUID.randomUUID().toString().substring(0, 6);
        String email = name + "@example.com";
        String token = register(name, email, ip());

        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(jsonPath("$.emailVerified").value(false));
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(postJson("hi")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("confirm your email")));
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.emailVerificationRequired").value(true));

        String verify = tokenFromEmail(email, "Confirm your email");
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + verify + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + verify + "\"}"))
                .andExpect(status().isBadRequest()); // single use

        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(postJson("hi")))
                .andExpect(status().isCreated());
    }

    @Test
    void passwordResetWorksOnceAndSignsOutOldSessions() throws Exception {
        String name = "r_" + UUID.randomUUID().toString().substring(0, 6);
        String email = name + "@example.com";
        String oldSession = register(name, email, ip());

        // Same answer for known and unknown emails, and no email for the unknown one.
        String known = mvc.perform(post("/api/auth/forgot-password").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email.toUpperCase() + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/forgot-password").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-" + name + "@example.com\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(known, unknown);
        verify(mail, never()).send(eq("nobody-" + name + "@example.com"), anyString(), anyString());

        String reset = tokenFromEmail(email, "Reset your Techy password");
        Thread.sleep(1100); // JWT times are in whole seconds: make the new session clearly newer than the old one
        String body = om.writeValueAsString(Map.of("token", reset, "password", "brand-new-pass"));
        String res = mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newSession = "Bearer " + om.readTree(res).get("token").asText();

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()); // link already used
        mvc.perform(get("/api/users/me").header("Authorization", oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", newSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(post("/api/auth/login").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", name, "password", "password123")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", name, "password", "brand-new-pass")))).andExpect(status().isOk());
    }

    @Test
    void botsAreSlowedDown() throws Exception {
        // Honeypot field filled in -> rejected.
        Map<String, String> bot = new HashMap<>(Map.of("username", "bot_" + UUID.randomUUID().toString().substring(0, 6),
                "email", "bot@example.com", "password", "password123", "website", "http://spam.example"));
        mvc.perform(post("/api/auth/register").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(bot))).andExpect(status().isBadRequest());

        // 5 sign-ups per hour per IP.
        String sameIp = ip();
        for (int i = 0; i < 5; i++) {
            String n = "rl_" + UUID.randomUUID().toString().substring(0, 6);
            register(n, n + "@example.com", sameIp);
        }
        String n = "rl_" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/auth/register").header("X-Real-IP", sameIp).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", n, "email", n + "@example.com", "password", "password123"))))
                .andExpect(status().isTooManyRequests());

        // 10 failed logins on one account within 15 minutes, even from different IPs.
        String victim = "lk_" + UUID.randomUUID().toString().substring(0, 6);
        register(victim, victim + "@example.com", ip());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of("username", victim, "password", "wrong-" + i)))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").header("X-Real-IP", ip()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("username", victim, "password", "password123")))).andExpect(status().isTooManyRequests());
    }

    @Test
    void listedEmailsBecomeModeratorsOnceVerified() throws Exception {
        String token = register("boss_" + UUID.randomUUID().toString().substring(0, 6), "boss@example.com", ip());
        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(jsonPath("$.admin").value(false));
        String verify = tokenFromEmail("boss@example.com", "Confirm your email");
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + verify + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/admin/reports").header("Authorization", token)).andExpect(status().isOk());
    }
}
