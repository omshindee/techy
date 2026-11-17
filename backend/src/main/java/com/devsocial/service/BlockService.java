package com.devsocial.service;

import com.devsocial.exception.ApiException;
import com.devsocial.model.Block;
import com.devsocial.model.User;
import com.devsocial.repository.BlockRepository;
import com.devsocial.repository.FollowRepository;
import com.devsocial.repository.NotificationRepository;
import com.devsocial.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Blocking is mutual in effect: once A blocks B, neither sees the other's posts, comments or
 * profile, they can't follow, like or comment on each other, and existing follows are removed.
 * Depends only on repositories so every other service can use it.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    /** A user id that never exists, so "id not in :hidden" queries always get a non-empty list. */
    private static final Long NO_ONE = -1L;

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;

    /** Everyone the viewer blocked plus everyone who blocked the viewer. Never empty. */
    public Set<Long> hiddenIds(User viewer) {
        Set<Long> ids = new HashSet<>();
        ids.add(NO_ONE);
        if (viewer != null) {
            ids.addAll(blockRepository.findBlockedIds(viewer.getId()));
            ids.addAll(blockRepository.findBlockerIds(viewer.getId()));
        }
        return ids;
    }

    public Set<Long> blockedByMe(User viewer) {
        return viewer == null ? Set.of() : blockRepository.findBlockedIds(viewer.getId());
    }

    public boolean blocked(User blocker, User blocked) {
        return blockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blocked.getId());
    }

    public boolean eitherBlocked(User a, User b) {
        return blocked(a, b) || blocked(b, a);
    }

    /** Throws 403 when either user blocked the other. */
    public void requireNotBlocked(User actor, User other, String message) {
        if (!actor.getId().equals(other.getId()) && eitherBlocked(actor, other)) {
            throw new ApiException(HttpStatus.FORBIDDEN, message);
        }
    }

    @Transactional
    public void block(User blocker, User target) {
        if (blocker.getId().equals(target.getId())) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't block yourself");
        if (!blocked(blocker, target)) blockRepository.save(new Block(blocker, target));
        followRepository.findByFollowerIdAndFollowingId(blocker.getId(), target.getId()).ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFollowingId(target.getId(), blocker.getId()).ifPresent(followRepository::delete);
        notificationRepository.deleteFollow(blocker.getId(), target.getId(), Notification.Type.FOLLOW);
        notificationRepository.deleteFollow(target.getId(), blocker.getId(), Notification.Type.FOLLOW);
    }

    @Transactional
    public void unblock(User blocker, User target) {
        blockRepository.findByBlockerIdAndBlockedId(blocker.getId(), target.getId()).ifPresent(blockRepository::delete);
    }
}
