package com.devsocial.service;

import com.devsocial.exception.ApiException;
import com.devsocial.model.EmailToken;
import com.devsocial.model.User;
import com.devsocial.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Issues and redeems the single-use links sent by email. */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final EmailTokenRepository repository;

    /** Creates a new link token (the raw value goes in the email) and retires older unused ones. */
    @Transactional
    public String issue(User user, EmailToken.Purpose purpose, Duration validFor) {
        repository.retireUnused(user.getId(), purpose, Instant.now());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EmailToken token = new EmailToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(validFor));
        repository.save(token);
        return raw;
    }

    /** Marks the token used and returns its user, or throws 400 if it's unknown, used or expired. */
    @Transactional
    public User redeem(String raw, EmailToken.Purpose purpose) {
        EmailToken token = repository.findByTokenHashAndPurpose(hash(raw == null ? "" : raw.trim()), purpose)
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired. Please request a new one."));
        token.setUsedAt(Instant.now());
        return token.getUser();
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
