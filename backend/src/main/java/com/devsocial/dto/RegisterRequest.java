package com.devsocial.dto;

import jakarta.validation.constraints.*;

/**
 * Sign-up form. {@code website} is a honeypot: the field is hidden from people, so a value means a bot.
 * {@code captchaToken} is the Turnstile response when the captcha is enabled.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may only contain letters, numbers, _ and -")
        String username,
        @Size(max = 50) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        String website,
        String captchaToken) {}
