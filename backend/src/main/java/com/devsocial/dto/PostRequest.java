package com.devsocial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code categories}: one to three; {@code category} alone is still accepted (older clients). */
public record PostRequest(@NotBlank @Size(max = 2000) String content, String category, List<String> categories) {}
