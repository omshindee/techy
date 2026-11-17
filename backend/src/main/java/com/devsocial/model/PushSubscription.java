package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One browser/phone that asked for push notifications for an account (see PushService). */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class PushSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** The push service URL for this device. Unique: a device belongs to whoever signed in last. */
    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    /** The browser's public key (base64url) and auth secret, used to encrypt each notification. */
    @Column(nullable = false, length = 128)
    private String p256dh;

    @Column(nullable = false, length = 64)
    private String auth;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
