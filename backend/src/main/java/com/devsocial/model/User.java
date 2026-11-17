package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique handle used in URLs (@username). */
    @Column(nullable = false, unique = true, length = 30)
    private String username;

    /** Free-form name shown on posts and profiles, e.g. "Priya Sharma". Optional. */
    @Column(length = 50)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 500)
    private String bio;

    private String avatarUrl;
    private String githubUrl;

    /** Comma-separated tags, e.g. "Java,Spring Boot,Linux". */
    @Column(length = 500)
    private String skills = "";

    /** Comma-separated categories from {@link Categories}. */
    @Column(length = 500)
    private String interests = "";

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified = false;

    /** USER or ADMIN (moderator). */
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'USER'")
    private String role = ROLE_USER;

    /** Suspended accounts can't sign in, and their posts and profile are hidden. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean suspended = false;

    /** Set when the password changes; login tokens issued before this are rejected. */
    private Instant credentialsChangedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** The display name, falling back to the username. */
    public String getNameToShow() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }
}
