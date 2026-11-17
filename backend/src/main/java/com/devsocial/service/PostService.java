package com.devsocial.service;

import com.devsocial.dto.PostRequest;
import com.devsocial.dto.PostResponse;
import com.devsocial.exception.ApiException;
import com.devsocial.exception.PostNotFoundException;
import com.devsocial.model.Categories;
import com.devsocial.model.Post;
import com.devsocial.model.User;
import com.devsocial.repository.CommentRepository;
import com.devsocial.repository.LikeRepository;
import com.devsocial.repository.PostRepository;
import com.devsocial.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int PAGE_SIZE = 50;
    private static final int MAX_CONTENT = 2000;

    private final MediaStorageService storage;
    private final MediaUrlSigner signer;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final RateLimiter rateLimiter;

    @Transactional
    public PostResponse create(String username, PostRequest req) {
        return create(username, req.content(), req.category(), null);
    }

    /**
     * Creates a text post, an image post, or an image post with a caption.
     * At least one of {@code content} and {@code image} is required.
     */
    @Transactional
    public PostResponse create(String username, String content, String category, MultipartFile image) {
        if (category == null || !Categories.ALL.contains(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown category: " + category);
        }
        String text = content == null ? "" : content.trim();
        boolean hasImage = image != null && !image.isEmpty();
        if (text.isEmpty() && !hasImage) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Write something or add a photo");
        }
        if (text.length() > MAX_CONTENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "content size must be between 0 and " + MAX_CONTENT);
        }
        User author = userService.getEntity(username);
        userService.requireVerified(author);
        rateLimiter.check("posts", username, 20, Duration.ofHours(1));
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(text);
        post.setCategory(category);
        if (hasImage) post.setImageKey(storage.store(image));
        postRepository.save(post);
        return toResponse(post, author);
    }

    /** A post the viewer may see: its author hasn't blocked them (either way) and isn't suspended. */
    public Post getVisible(Long id, User viewer) {
        Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        User author = post.getAuthor();
        if (!userService.canSee(viewer, author) || blockService.blocked(viewer, author)) throw new PostNotFoundException(id);
        return post;
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long id, String viewerUsername) {
        User viewer = userService.getEntity(viewerUsername);
        return toResponse(getVisible(id, viewer), viewer);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> list(String category, String authorUsername, String viewerUsername) {
        User viewer = userService.getEntity(viewerUsername);
        PageRequest page = PageRequest.of(0, PAGE_SIZE);
        List<Post> posts;
        if (authorUsername != null && !authorUsername.isBlank()) {
            User author = userService.getVisible(authorUsername, viewer);
            // You blocked them: their profile stays reachable (to unblock), their posts don't show.
            posts = blockService.blocked(viewer, author) ? List.of()
                    : postRepository.findByAuthorUsernameOrderByCreatedAtDesc(authorUsername, page);
        } else if (category != null && !category.isBlank()) {
            posts = postRepository.recentInCategory(category, blockService.hiddenIds(viewer), page);
        } else {
            posts = postRepository.recent(blockService.hiddenIds(viewer), page);
        }
        return toResponses(posts, viewer);
    }

    /** Authors delete their own posts; moderators can delete any post. */
    @Transactional
    public void delete(Long id, String username) {
        Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        User user = userService.getEntity(username);
        if (!post.getAuthor().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only delete your own posts");
        }
        remove(post);
    }

    /** Deletes a post with its likes, comments and photo. */
    @Transactional
    public void remove(Post post) {
        commentRepository.deleteByPostId(post.getId());
        likeRepository.deleteByPostId(post.getId());
        postRepository.delete(post);
        storage.deleteAfterCommit(post.getImageKey());
    }

    public PostResponse toResponse(Post post, User viewer) {
        return toResponses(List.of(post), viewer).get(0);
    }

    /** Maps posts with like/comment counts computed in batch (3 queries total). Image links are signed. */
    public List<PostResponse> toResponses(List<Post> posts, User viewer) {
        if (posts.isEmpty()) return List.of();
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, Long> likes = toMap(likeRepository.countByPostIds(ids));
        Map<Long, Long> comments = toMap(commentRepository.countByPostIds(ids));
        Set<Long> liked = viewer == null ? Set.of() : new HashSet<>(likeRepository.findLikedPostIds(viewer.getId(), ids));
        return posts.stream().map(p -> new PostResponse(
                p.getId(), p.getContent(), p.getCategory(), signer.sign(MediaStorageService.urlFor(p.getImageKey())),
                p.getCreatedAt(), userService.author(p.getAuthor()),
                likes.getOrDefault(p.getId(), 0L), comments.getOrDefault(p.getId(), 0L),
                liked.contains(p.getId()))).toList();
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return map;
    }
}
