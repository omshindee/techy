package com.devsocial.service;

import com.devsocial.dto.NotificationDtos.NotificationPage;
import com.devsocial.dto.NotificationDtos.NotificationResponse;
import com.devsocial.model.Comment;
import com.devsocial.model.Notification;
import com.devsocial.model.Notification.Type;
import com.devsocial.model.Post;
import com.devsocial.model.User;
import com.devsocial.repository.FollowRepository;
import com.devsocial.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Creates, lists and cleans up notifications. Creation is called from the follow/like/comment services
 * inside their transactions, so a notification exists exactly when the action does.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 30;

    private final NotificationRepository repository;
    private final FollowRepository followRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final MediaUrlSigner signer;
    private final PushService pushService;

    // --- created by actions -------------------------------------------------------------------

    public void followed(User actor, User target) {
        if (same(actor, target) || repository.existsByActorIdAndRecipientIdAndType(actor.getId(), target.getId(), Type.FOLLOW)) return;
        repository.save(new Notification(target, actor, Type.FOLLOW, null));
        pushService.notify(target, new PushService.Push("New follower", actor.getNameToShow() + " started following you.",
                "/profile/" + actor.getUsername(), "follow-" + actor.getUsername(), false));
    }

    public void unfollowed(User actor, User target) {
        repository.deleteFollow(actor.getId(), target.getId(), Type.FOLLOW);
    }

    public void liked(User actor, Post post) {
        if (same(actor, post.getAuthor()) || repository.existsByActorIdAndPostIdAndType(actor.getId(), post.getId(), Type.LIKE)) return;
        repository.save(new Notification(post.getAuthor(), actor, Type.LIKE, post));
        pushService.notify(post.getAuthor(), new PushService.Push(actor.getNameToShow() + " liked your post",
                snippet(post.getContent()), "/post/" + post.getId(), "like-" + post.getId(), false));
    }

    public void unliked(User actor, Post post) {
        repository.deleteForPost(actor.getId(), post.getId(), Type.LIKE);
    }

    public void commented(User actor, Comment comment) {
        Post post = comment.getPost();
        if (same(actor, post.getAuthor())) return;
        Notification n = new Notification(post.getAuthor(), actor, Type.COMMENT, post);
        n.setCommentId(comment.getId());
        n.setSnippet(snippet(comment.getContent()));
        repository.save(n);
        pushService.notify(post.getAuthor(), new PushService.Push(actor.getNameToShow() + " commented on your post",
                n.getSnippet(), "/post/" + post.getId(), "comment-" + comment.getId(), false));
    }

    public void commentDeleted(Long commentId) {
        repository.deleteByCommentId(commentId);
    }

    public void postDeleted(Long postId) {
        repository.deleteByPostId(postId);
    }

    // --- read by the recipient ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotificationPage list(String username, Long before) {
        User me = userService.getEntity(username);
        List<Notification> rows = repository.page(me.getId(), before == null ? Long.MAX_VALUE : before,
                hidden(me), PageRequest.of(0, PAGE_SIZE + 1));
        boolean more = rows.size() > PAGE_SIZE;
        if (more) rows = rows.subList(0, PAGE_SIZE);
        Set<Long> following = followRepository.findFollowingIds(me.getId());
        List<NotificationResponse> items = rows.stream().map(n -> toResponse(n, following)).toList();
        return new NotificationPage(items, more ? rows.get(rows.size() - 1).getId() : null);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        User me = userService.getEntity(username);
        return repository.countUnread(me.getId(), hidden(me));
    }

    @Transactional
    public void markAllRead(String username) {
        repository.markAllRead(userService.getEntity(username).getId(), Instant.now());
    }

    private NotificationResponse toResponse(Notification n, Set<Long> following) {
        Post p = n.getPost();
        String postText = p == null ? null : snippet(p.getContent());
        String image = p == null ? null : signer.sign(MediaStorageService.urlFor(p.getImageKey()));
        return new NotificationResponse(n.getId(), n.getType().name(), userService.author(n.getActor()),
                following.contains(n.getActor().getId()), p == null ? null : p.getId(), image, postText,
                n.getSnippet(), n.getCreatedAt(), n.getReadAt() != null);
    }

    /** People hidden from me (blocked either way); BlockService never returns an empty set. */
    private Set<Long> hidden(User me) {
        return blockService.hiddenIds(me);
    }

    private static boolean same(User a, User b) {
        return a.getId().equals(b.getId());
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() <= Notification.SNIPPET_LENGTH ? t : t.substring(0, Notification.SNIPPET_LENGTH - 1) + "…";
    }
}
