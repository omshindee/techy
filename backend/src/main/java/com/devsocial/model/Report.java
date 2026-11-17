package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user flagging a post, comment or account for moderators. One report per reporter and target. */
@Entity
@Table(name = "reports", uniqueConstraints = @UniqueConstraint(columnNames = {"reporter_id", "target_type", "target_id"}))
@Getter @Setter @NoArgsConstructor
public class Report {
    public enum TargetType { POST, COMMENT, USER, MESSAGE }
    public enum Reason { SPAM, HARASSMENT, HATE, NUDITY, VIOLENCE, SCAM, OTHER }
    public enum Status { OPEN, RESOLVED, DISMISSED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    /** What the moderator did, e.g. "Removed post", "Suspended @spammer", "Dismissed". */
    @Column(length = 80)
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    private Instant resolvedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
