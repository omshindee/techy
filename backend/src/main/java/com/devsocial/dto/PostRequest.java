package com.devsocial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(@NotBlank @Size(max = 2000) String content, @NotBlank String category) {}
