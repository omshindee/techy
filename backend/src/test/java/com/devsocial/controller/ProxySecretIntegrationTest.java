package com.devsocial.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * With PROXY_SECRET set (Cloudflare Pages deployment), only requests through the Pages proxy reach
 * the API, the proxy's X-Real-IP is trusted for rate limits, and health checks stay open.
 */
@SpringBootTest(properties = {
        "app.security.proxy-secret=s3cret-from-pages-proxy",
        "app.ratelimit.enabled=true",
        "app.cors.allowed-origins=https://techy.example.com,https://*.techy.pages.dev"})
@AutoConfigureMockMvc
class ProxySecretIntegrationTest {

    private static final String SECRET = "s3cret-from-pages-proxy";

    @Autowired MockMvc mvc;

    @Test
    void apiRequiresTheProxySecret() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Direct API access is not allowed. Use the Techy website."));
        mvc.perform(get("/api/auth/config").header("X-Proxy-Secret", "wrong")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/config").header("X-Proxy-Secret", SECRET)).andExpect(status().isOk());
        // Hosts' health checks don't go through the proxy.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void proxiedVisitorIpIsUsedForRateLimits() throws Exception {
        // 5 sign-ups per hour per IP: a 6th from the same visitor IP is refused...
        for (int i = 0; i < 5; i++) register("10.9.8.7").andExpect(status().isCreated());
        register("10.9.8.7").andExpect(status().isTooManyRequests());
        // ...while a different visitor behind the same proxy is unaffected.
        register("10.9.8.8").andExpect(status().isCreated());
    }

    @Test
    void corsAllowsConfiguredOriginsAndPreviewWildcards() throws Exception {
        for (String origin : new String[]{"https://techy.example.com", "https://abc123.techy.pages.dev"}) {
            mvc.perform(options("/api/auth/login").header("Origin", origin).header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
        mvc.perform(options("/api/auth/login").header("Origin", "https://evil.example.org").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions register(String visitorIp) throws Exception {
        String name = "px_" + UUID.randomUUID().toString().substring(0, 8);
        return mvc.perform(post("/api/auth/register").header("X-Proxy-Secret", SECRET).header("X-Real-IP", visitorIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.com\",\"password\":\"password123\"}"));
    }
}
