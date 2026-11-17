package com.devsocial.service;

import com.devsocial.dto.CommentRequest;
import com.devsocial.dto.CommentResponse;
import com.devsocial.exception.ApiException;
import com.devsocial.model.Comment;
import com.devsocial.model.Post;
import com.devsocial.model.User;
import com.devsocial.repository.CommentRepository;
import com.devsocial.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostService postService;
    private final UserService userService;
    private final BlockService blockService;
    private final RateLimiter rateLimiter;
    private final NotificationService notificationService;

    @Transactional
    public CommentResponse add(Long postId, String username, CommentRequest req) {
        User author = userService.getEntity(username);
        Post post = postService.getVisible(postId, author);
        userService.requireVerified(author);
        blockService.requireNotBlocked(author, post.getAuthor(), "You can't comment on this post");
        rateLimiter.check("comments", username, 60, Duration.ofHours(1));
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(req.content().trim());
        commentRepository.save(comment);
        notificationService.commented(author, comment);
        return toResponse(comment);
    }

    /** Comments on a post, minus those by people the viewer blocked (or who blocked them) and suspended accounts. */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long postId, String viewerUsername) {
        User viewer = userService.getEntity(viewerUsername);
        postService.getVisible(postId, viewer);
        Set<Long> hidden = blockService.hiddenIds(viewer);
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(c -> !hidden.contains(c.getAuthor().getId()) && !c.getAuthor().isSuspended())
                .map(this::toResponse).toList();
    }

    /** The comment's author, the post's author, or a moderator may delete a comment. */
    @Transactional
    public void delete(Long postId, Long commentId, String username) {
        User user = userService.getEntity(username);
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getPost().getId().equals(postId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
        boolean allowed = comment.getAuthor().getId().equals(user.getId())
                || comment.getPost().getAuthor().getId().equals(user.getId())
                || user.isAdmin();
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "You can't delete this comment");
        notificationService.commentDeleted(comment.getId());
        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment c) {
        return new CommentResponse(c.getId(), c.getContent(), c.getCreatedAt(), userService.author(c.getAuthor()));
    }
}
