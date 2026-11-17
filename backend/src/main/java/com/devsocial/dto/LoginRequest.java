package com.devsocial.dto;

import jakarta.validation.constraints.NotBlank;

/** "username" may be either the username or the email address. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
