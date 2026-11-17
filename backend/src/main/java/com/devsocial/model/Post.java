package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Getter @Setter @NoArgsConstructor
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    /** The post text, or the caption of an image post (may be empty then). */
    @Column(nullable = false, length = 2000)
    private String content;

    /** Storage key of the attached image, if any (see MediaStorageService). */
    @Column(length = 64)
    private String imageKey;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
