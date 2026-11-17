package com.devsocial.dto;

import com.devsocial.model.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request/response shapes for direct messages. */
public final class MessageDtos {
    private MessageDtos() {}

    public record SendMessageRequest(@NotBlank @Size(max = Message.MAX_LENGTH) String content) {}

    public record MessageResponse(Long id, Long senderId, boolean mine, String content, Instant createdAt) {}

    /**
     * One chat with {@code other}. {@code seenUpToId}: the newest of your messages they've read.
     * {@code canSend}/{@code cannotSendReason} explain whether you can write (e.g. "Follow @x to message them").
     */
    public record ThreadResponse(Long conversationId, PostResponse.Author other, boolean followingOther,
                                 List<MessageResponse> messages, boolean hasOlder, Long seenUpToId,
                                 boolean canSend, String cannotSendReason) {}

    public record ConversationSummary(Long id, PostResponse.Author other, String lastMessage, boolean lastMessageMine,
                                      Instant lastMessageAt, long unread) {}

    public record UnreadCount(long count) {}
}
