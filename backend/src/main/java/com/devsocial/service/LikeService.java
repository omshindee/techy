package com.devsocial.service;

import com.devsocial.dto.LikeResponse;
import com.devsocial.model.Like;
import com.devsocial.model.Post;
import com.devsocial.model.User;
import com.devsocial.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostService postService;
    private final UserService userService;
    private final BlockService blockService;

    @Transactional
    public LikeResponse like(Long postId, String username) {
        User user = userService.getEntity(username);
        Post post = postService.getVisible(postId, user);
        userService.requireVerified(user);
        blockService.requireNotBlocked(user, post.getAuthor(), "You can't like this post");
        if (likeRepository.findByPostIdAndUserId(postId, user.getId()).isEmpty()) {
            likeRepository.save(new Like(post, user));
        }
        return new LikeResponse(true, likeRepository.countByPostId(postId));
    }

    /** A current like/unlike state, e.g. after losing a race with a concurrent like. */
    @Transactional(readOnly = true)
    public LikeResponse state(Long postId, String username) {
        User user = userService.getEntity(username);
        boolean liked = likeRepository.findByPostIdAndUserId(postId, user.getId()).isPresent();
        return new LikeResponse(liked, likeRepository.countByPostId(postId));
    }

    @Transactional
    public LikeResponse unlike(Long postId, String username) {
        User user = userService.getEntity(username);
        postService.getVisible(postId, user);
        likeRepository.findByPostIdAndUserId(postId, user.getId()).ifPresent(likeRepository::delete);
        return new LikeResponse(false, likeRepository.countByPostId(postId));
    }
}
