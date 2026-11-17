package com.devsocial.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /** The main (first) category. Kept for older posts, which only have this one. */
    @Column(nullable = false, length = 40)
    private String category;

    /** All categories, in the order picked (the first equals {@link #category}). Empty on posts made before
     *  multiple categories existed; {@link #allCategories()} covers both. */
    @ElementCollection
    @CollectionTable(name = "post_categories", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "category", nullable = false, length = 40)
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 64)
    private List<String> categories = new ArrayList<>();

    public List<String> allCategories() {
        return categories == null || categories.isEmpty() ? List.of(category) : List.copyOf(categories);
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
