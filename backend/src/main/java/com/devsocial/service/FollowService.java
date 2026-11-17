package com.devsocial.service;

import com.devsocial.dto.UserResponse;
import com.devsocial.exception.ApiException;
import com.devsocial.model.Follow;
import com.devsocial.model.User;
import com.devsocial.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final NotificationService notificationService;

    @Transactional
    public UserResponse follow(String viewerUsername, String targetUsername) {
        User viewer = userService.getEntity(viewerUsername);
        User target = userService.getVisible(targetUsername, viewer);
        if (viewer.getId().equals(target.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't follow yourself");
        }
        userService.requireVerified(viewer);
        blockService.requireNotBlocked(viewer, target, "You can't follow this account");
        if (!followRepository.existsByFollowerIdAndFollowingId(viewer.getId(), target.getId())) {
            followRepository.save(new Follow(viewer, target));
            notificationService.followed(viewer, target);
        }
        return userService.toResponse(target, viewer);
    }

    @Transactional
    public UserResponse unfollow(String viewerUsername, String targetUsername) {
        User viewer = userService.getEntity(viewerUsername);
        User target = userService.getEntity(targetUsername);
        followRepository.findByFollowerIdAndFollowingId(viewer.getId(), target.getId())
                .ifPresent(followRepository::delete);
        notificationService.unfollowed(viewer, target);
        return userService.toResponse(target, viewer);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> followers(String username, String viewerUsername) {
        User viewer = userService.getEntity(viewerUsername);
        User user = userService.getVisible(username, viewer);
        return userService.toResponses(visible(followRepository.findByFollowingIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(Follow::getFollower).toList(), viewer), viewer);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> following(String username, String viewerUsername) {
        User viewer = userService.getEntity(viewerUsername);
        User user = userService.getVisible(username, viewer);
        return userService.toResponses(visible(followRepository.findByFollowerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(Follow::getFollowing).toList(), viewer), viewer);
    }

    /** Drops blocked (either way) and suspended accounts from a list. */
    private List<User> visible(List<User> users, User viewer) {
        Set<Long> hidden = blockService.hiddenIds(viewer);
        return users.stream().filter(u -> !hidden.contains(u.getId()) && (!u.isSuspended() || viewer.isAdmin())).toList();
    }
}
