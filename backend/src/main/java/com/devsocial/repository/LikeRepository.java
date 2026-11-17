package com.devsocial.repository;

import com.devsocial.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    void deleteByPostId(Long postId);

    @Query("select l.post.id, count(l) from PostLike l where l.post.id in :ids group by l.post.id")
    List<Object[]> countByPostIds(@Param("ids") Collection<Long> ids);

    @Query("select l.post.id from PostLike l where l.user.id = :userId and l.post.id in :ids")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
