package com.devsocial.security;

import com.devsocial.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Cloudflare Turnstile captcha check (free: https://www.cloudflare.com/products/turnstile/).
 * Off until both TURNSTILE_SITE_KEY and TURNSTILE_SECRET_KEY are set.
 */
@Component
public class CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(CaptchaVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String siteKey;
    private final String secretKey;
    private final RestClient client;

    public CaptchaVerifier(@Value("${app.captcha.turnstile.site-key}") String siteKey,
                           @Value("${app.captcha.turnstile.secret-key}") String secretKey) {
        this.siteKey = siteKey == null ? "" : siteKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return !siteKey.isEmpty() && !secretKey.isEmpty();
    }

    /** The public key the browser widget needs, or null while the captcha is off. */
    public String siteKey() {
        return enabled() ? siteKey : null;
    }

    public void verify(String token, String ip) {
        if (!enabled()) return;
        if (token == null || token.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please complete the captcha.");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (ip != null) form.add("remoteip", ip);
        Map<?, ?> result;
        try {
            result = client.post().uri(VERIFY_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
        } catch (RestClientException e) {
            log.error("Captcha verification request failed", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't check the captcha right now. Please try again.");
        }
        if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The captcha check failed. Please try again.");
        }
    }
}
