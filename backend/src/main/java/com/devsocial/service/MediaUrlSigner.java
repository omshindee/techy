package com.devsocial.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Image privacy. Every image URL the API hands out carries an expiry and an HMAC signature,
 * e.g. /api/media/abc.jpg?exp=1700000000&sig=... The media endpoint refuses unsigned, tampered
 * or expired links, so only signed-in users get working links and copied links stop working.
 *
 * Expiry is rounded to fixed windows so an image keeps the same URL for hours and browsers can
 * cache it: each link is valid for between ttl and 2*ttl.
 */
@Component
public class MediaUrlSigner {

    private final byte[] secret;
    private final long ttlSeconds;

    public MediaUrlSigner(@Value("${app.media.signing-secret:${app.jwt.secret}}") String secret,
                          @Value("${app.media.url-ttl-hours:12}") long ttlHours) {
        // Derived key, so a media signature can never double as anything else signed with the JWT secret.
        this.secret = ("media:" + secret).getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(1, ttlHours) * 3600;
    }

    /** Signs an internal media URL; null and external URLs pass through unchanged. */
    public String sign(String url) {
        if (url == null || !url.startsWith(MediaStorageService.URL_PREFIX)) return url;
        String key = url.substring(MediaStorageService.URL_PREFIX.length());
        long exp = (Instant.now().getEpochSecond() / ttlSeconds + 2) * ttlSeconds;
        return url + "?exp=" + exp + "&sig=" + hmac(key + ":" + exp);
    }

    public boolean verify(String key, Long exp, String sig) {
        if (key == null || exp == null || sig == null) return false;
        if (exp < Instant.now().getEpochSecond()) return false;
        return MessageDigest.isEqual(hmac(key + ":" + exp).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    /** Seconds until a link expires (used for Cache-Control). */
    public static long secondsLeft(long exp) {
        return Math.max(0, exp - Instant.now().getEpochSecond());
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
