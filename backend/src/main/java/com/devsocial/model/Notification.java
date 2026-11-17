package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * "Someone did something involving you": followed you, liked your post or commented on it.
 * Removed again when the action is undone (unfollow, unlike, comment or post deleted).
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "recipient_id, id"),
        @Index(name = "idx_notifications_post", columnList = "post_id"),
        @Index(name = "idx_notifications_comment", columnList = "comment_id")
})
@Getter @Setter @NoArgsConstructor
public class Notification {

    public enum Type { FOLLOW, LIKE, COMMENT }

    /** How much of a comment is kept for the notification preview. */
    public static final int SNIPPET_LENGTH = 140;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;

    /** The post that was liked or commented on (null for follows). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /** The comment, for COMMENT notifications. Not a foreign key, so deleting comments stays simple. */
    @Column(name = "comment_id")
    private Long commentId;

    @Column(length = SNIPPET_LENGTH)
    private String snippet;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant readAt;

    public Notification(User recipient, User actor, Type type, Post post) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.post = post;
    }
}
