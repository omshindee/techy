package com.devsocial.repository;

import com.devsocial.model.Block;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BlockRepository extends JpaRepository<Block, Long> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("select b.blocked.id from Block b where b.blocker.id = :userId")
    Set<Long> findBlockedIds(@Param("userId") Long userId);

    @Query("select b.blocker.id from Block b where b.blocked.id = :userId")
    Set<Long> findBlockerIds(@Param("userId") Long userId);

    @EntityGraph(attributePaths = "blocked")
    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);
}
