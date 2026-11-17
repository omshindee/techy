package com.devsocial.repository;

import com.devsocial.model.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    void deleteByPostId(Long postId);

    @Query("select c.post.id, count(c) from Comment c where c.post.id in :ids group by c.post.id")
    List<Object[]> countByPostIds(@Param("ids") Collection<Long> ids);
}
