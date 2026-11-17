package com.devsocial.repository;

import com.devsocial.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    /** Newest first; reversed by the service for display. */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);

    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId, Pageable pageable);

    boolean existsByConversationIdAndSenderId(Long conversationId, Long senderId);

    /** The newest message I sent that the other person has read ("Seen"). */
    @Query("select max(m.id) from Message m where m.conversation.id = :conversationId and m.sender.id = :senderId and m.readAt is not null")
    Long lastReadIdSentBy(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    @Modifying
    @Query("update Message m set m.readAt = :now where m.conversation.id = :conversationId and m.sender.id <> :readerId and m.readAt is null")
    int markRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId, @Param("now") Instant now);

    /** Rows of [conversationId, unreadCount] for messages sent to {@code userId}. */
    @Query("""
            select m.conversation.id, count(m) from Message m
            where (m.conversation.userA.id = :userId or m.conversation.userB.id = :userId)
              and m.sender.id <> :userId and m.readAt is null
            group by m.conversation.id
            """)
    List<Object[]> unreadByConversation(@Param("userId") Long userId);

    /** The latest message of each given conversation. */
    @EntityGraph(attributePaths = "sender")
    @Query("select m from Message m where m.id in (select max(m2.id) from Message m2 where m2.conversation.id in :ids group by m2.conversation.id)")
    List<Message> latestIn(@Param("ids") Collection<Long> ids);
}
