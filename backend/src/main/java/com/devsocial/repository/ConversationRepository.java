package com.devsocial.repository;

import com.devsocial.model.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @EntityGraph(attributePaths = {"userA", "userB"})
    @Query("select c from Conversation c where c.userA.id = :userId or c.userB.id = :userId order by c.lastMessageAt desc")
    List<Conversation> findForUser(@Param("userId") Long userId, Pageable pageable);
}
