package com.devsocial.repository;

import com.devsocial.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Newest first, skipping notifications from hidden people (blocked either way) and suspended accounts.
     * {@code hidden} must not be empty (pass a placeholder id such as -1).
     */
    @EntityGraph(attributePaths = {"actor", "post"})
    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId and n.id < :beforeId
              and n.actor.id not in :hidden and n.actor.suspended = false
            order by n.id desc
            """)
    List<Notification> page(@Param("recipientId") Long recipientId, @Param("beforeId") Long beforeId,
                            @Param("hidden") Collection<Long> hidden, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.recipient.id = :recipientId and n.readAt is null
              and n.actor.id not in :hidden and n.actor.suspended = false
            """)
    long countUnread(@Param("recipientId") Long recipientId, @Param("hidden") Collection<Long> hidden);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

    boolean existsByActorIdAndRecipientIdAndType(Long actorId, Long recipientId, Notification.Type type);

    boolean existsByActorIdAndPostIdAndType(Long actorId, Long postId, Notification.Type type);

    @Modifying
    @Query("delete from Notification n where n.actor.id = :actorId and n.recipient.id = :recipientId and n.type = :type")
    void deleteFollow(@Param("actorId") Long actorId, @Param("recipientId") Long recipientId,
                      @Param("type") Notification.Type type);

    @Modifying
    @Query("delete from Notification n where n.actor.id = :actorId and n.post.id = :postId and n.type = :type")
    void deleteForPost(@Param("actorId") Long actorId, @Param("postId") Long postId,
                       @Param("type") Notification.Type type);

    @Modifying
    @Query("delete from Notification n where n.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("delete from Notification n where n.commentId = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);
}
