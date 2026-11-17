package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A direct message inside a {@link Conversation}. {@code readAt} is set when the recipient opens the chat. */
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_conversation_id", columnList = "conversation_id, id"))
@Getter @Setter @NoArgsConstructor
public class Message {
    public static final int MAX_LENGTH = 2000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant readAt;
}
