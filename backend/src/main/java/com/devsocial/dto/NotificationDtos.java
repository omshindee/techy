package com.devsocial.dto;

import java.time.Instant;
import java.util.List;

/** Response shapes for the notifications tab. */
public final class NotificationDtos {
    private NotificationDtos() {}

    /**
     * One notification. {@code postId}/{@code postImageUrl}/{@code postText} describe the post that was liked or
     * commented on; {@code snippet} is the start of the comment; {@code followingActor}: you follow them back.
     */
    public record NotificationResponse(Long id, String type, PostResponse.Author actor, boolean followingActor,
                                       Long postId, String postImageUrl, String postText, String snippet,
                                       Instant createdAt, boolean read) {}

    /** {@code nextBefore}: pass as {@code before} to load older ones (null when there are no more). */
    public record NotificationPage(List<NotificationResponse> items, Long nextBefore) {}

    public record UnreadCount(long count) {}
}
