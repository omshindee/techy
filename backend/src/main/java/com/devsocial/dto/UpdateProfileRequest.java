package com.devsocial.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Profile fields. The profile photo is uploaded separately via PUT /api/users/me/avatar. */
public record UpdateProfileRequest(
        @Size(max = 50) String displayName,
        @Size(max = 500) String bio,
        @Size(max = 255) String githubUrl,
        List<String> skills,
        List<String> interests) {}
