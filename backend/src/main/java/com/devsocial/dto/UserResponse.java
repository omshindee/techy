package com.devsocial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A user as seen by the viewer. {@code emailVerified} and {@code admin} are only filled in on your
 * own account; {@code suspended} only for moderators. Null fields are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id, String username, String displayName, String bio, String avatarUrl, String githubUrl,
        List<String> skills, List<String> interests,
        long followerCount, long followingCount, long postCount,
        boolean followedByMe, boolean blockedByMe,
        Boolean emailVerified, Boolean admin, Boolean suspended) {}
