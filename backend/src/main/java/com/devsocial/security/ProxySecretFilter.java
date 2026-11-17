package com.devsocial.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When PROXY_SECRET is configured, the API only answers requests that came through the Cloudflare
 * Pages proxy (which adds the secret). Someone who finds the backend's own URL can't bypass the
 * proxy, fake their IP, or skip the rate limits. /actuator/health stays open for the host's
 * health checks. Does nothing when PROXY_SECRET is empty (local development, docker-compose).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ProxySecretFilter extends OncePerRequestFilter {

    private final ClientIp clientIp;

    public ProxySecretFilter(ClientIp clientIp) {
        this.clientIp = clientIp;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !clientIp.proxySecretConfigured()
                || !request.getRequestURI().startsWith("/api/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (clientIp.fromTrustedProxy(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Direct API access is not allowed. Use the Techy website.\"}");
    }
}
