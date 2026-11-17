package com.devsocial.repository;

import com.devsocial.model.Follow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);
    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);
    long countByFollowingId(Long userId);
    long countByFollowerId(Long userId);

    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFollowingIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "following")
    List<Follow> findByFollowerIdOrderByCreatedAtDesc(Long userId);

    @Query("select f.following.id from Follow f where f.follower.id = :userId")
    Set<Long> findFollowingIds(@Param("userId") Long userId);

    /** Rows of [userId, followerCount] for the given users. */
    @Query("select f.following.id, count(f) from Follow f where f.following.id in :ids group by f.following.id")
    List<Object[]> countFollowersByUserIds(@Param("ids") Collection<Long> ids);

    /** Rows of [userId, followingCount] for the given users. */
    @Query("select f.follower.id, count(f) from Follow f where f.follower.id in :ids group by f.follower.id")
    List<Object[]> countFollowingByUserIds(@Param("ids") Collection<Long> ids);
}
