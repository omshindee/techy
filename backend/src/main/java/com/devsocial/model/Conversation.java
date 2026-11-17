package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-to-one chat. The two people are stored in a fixed order (lower user id first) so each pair
 * has exactly one conversation.
 */
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"}))
@Getter @Setter @NoArgsConstructor
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id")
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id")
    private User userB;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** When the newest message was sent; orders the inbox. */
    @Column(nullable = false)
    private Instant lastMessageAt = Instant.now();

    public Conversation(User one, User two) {
        boolean oneFirst = one.getId() < two.getId();
        this.userA = oneFirst ? one : two;
        this.userB = oneFirst ? two : one;
    }

    public boolean includes(User u) {
        return userA.getId().equals(u.getId()) || userB.getId().equals(u.getId());
    }

    public User otherThan(User me) {
        return userA.getId().equals(me.getId()) ? userB : userA;
    }
}
