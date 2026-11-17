package com.devsocial.repository;

import com.devsocial.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Listing queries skip suspended authors and the viewer's blocked users ({@code hidden}: never empty). */
public interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = "author")
    @Query("select p from Post p where p.author.suspended = false and p.author.id not in :hidden order by p.createdAt desc")
    List<Post> recent(@Param("hidden") Collection<Long> hidden, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("select p from Post p where p.category = :category and p.author.suspended = false and p.author.id not in :hidden order by p.createdAt desc")
    List<Post> recentInCategory(@Param("category") String category, @Param("hidden") Collection<Long> hidden, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    List<Post> findByAuthorUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /** Rows of [authorId, postCount] for the given users. */
    @Query("select p.author.id, count(p) from Post p where p.author.id in :ids group by p.author.id")
    List<Object[]> countByAuthorIds(@Param("ids") Collection<Long> ids);
}
