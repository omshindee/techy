package com.devsocial.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The caller's IP address, for rate limiting. Proxy headers (X-Real-IP / X-Forwarded-For) are only
 * trusted when the request provably came through our own proxy, because anyone who can reach the
 * API directly could otherwise fake them to dodge the limits:
 *  - TRUST_PROXY_HEADERS=true: the backend is only reachable through nginx (docker-compose), or
 *  - PROXY_SECRET is set and the request carries it in X-Proxy-Secret (Cloudflare Pages Function).
 */
@Component
public class ClientIp {

    public static final String SECRET_HEADER = "X-Proxy-Secret";

    private final boolean trustProxyHeaders;
    private final byte[] proxySecret;

    public ClientIp(@Value("${app.security.trust-proxy-headers}") boolean trustProxyHeaders,
                    @Value("${app.security.proxy-secret:}") String proxySecret) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.proxySecret = proxySecret == null || proxySecret.isBlank() ? null : proxySecret.trim().getBytes(StandardCharsets.UTF_8);
    }

    public boolean proxySecretConfigured() {
        return proxySecret != null;
    }

    /** True when the request carries the configured proxy secret (constant-time comparison). */
    public boolean fromTrustedProxy(HttpServletRequest request) {
        if (proxySecret == null) return false;
        String given = request.getHeader(SECRET_HEADER);
        return given != null && MessageDigest.isEqual(proxySecret, given.trim().getBytes(StandardCharsets.UTF_8));
    }

    public String of(HttpServletRequest request) {
        if (trustProxyHeaders || fromTrustedProxy(request)) {
            String real = request.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
