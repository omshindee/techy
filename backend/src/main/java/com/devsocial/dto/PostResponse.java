package com.devsocial.dto;

import java.time.Instant;
import java.util.List;

public record PostResponse(
        Long id, String content, String category, List<String> categories, String imageUrl, Instant createdAt, Author author,
        long likeCount, long commentCount, boolean likedByMe) {

    /** {@code displayName} falls back to the username when none is set. */
    public record Author(Long id, String username, String displayName, String avatarUrl) {}
}
