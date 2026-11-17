package com.devsocial.service;

import com.devsocial.dto.PostResponse;
import com.devsocial.model.Post;
import com.devsocial.model.User;
import com.devsocial.repository.FollowRepository;
import com.devsocial.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.1 feed: a transparent, hand-tuned score over the 200 most recent posts (skipping blocked and
 * suspended authors).
 * score = followed author (+50) + matches an interest (+25) + 2/like + 3/comment + recency (40 decaying over ~a day)
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int CANDIDATES = 200;
    private static final int FEED_SIZE = 50;

    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final PostService postService;
    private final UserService userService;
    private final BlockService blockService;

    @Transactional(readOnly = true)
    public List<PostResponse> feed(String username) {
        User viewer = userService.getEntity(username);
        Set<Long> following = followRepository.findFollowingIds(viewer.getId());
        Set<String> interests = new HashSet<>(UserService.split(viewer.getInterests()));
        List<Post> candidates = postRepository.recent(blockService.hiddenIds(viewer), PageRequest.of(0, CANDIDATES));
        Instant now = Instant.now();
        return postService.toResponses(candidates, viewer).stream()
                .sorted(Comparator.comparingDouble((PostResponse r) -> -score(
                        r, following.contains(r.author().id()), r.categories().stream().anyMatch(interests::contains), now)))
                .limit(FEED_SIZE)
                .toList();
    }

    public static double score(PostResponse r, boolean followedAuthor, boolean matchesInterest, Instant now) {
        double hours = Math.max(0, Duration.between(r.createdAt(), now).toMinutes() / 60.0);
        double recency = 40 * Math.exp(-hours / 24.0);
        return (followedAuthor ? 50 : 0) + (matchesInterest ? 25 : 0)
                + 2.0 * r.likeCount() + 3.0 * r.commentCount() + recency;
    }
}
