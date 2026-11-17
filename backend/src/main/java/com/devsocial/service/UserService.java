package com.devsocial.service;

import com.devsocial.dto.AccountRequests.Page;
import com.devsocial.dto.PostResponse;
import com.devsocial.dto.UpdateProfileRequest;
import com.devsocial.dto.UserResponse;
import com.devsocial.exception.ApiException;
import com.devsocial.exception.UserNotFoundException;
import com.devsocial.model.Block;
import com.devsocial.model.Categories;
import com.devsocial.model.User;
import com.devsocial.repository.BlockRepository;
import com.devsocial.repository.FollowRepository;
import com.devsocial.repository.PostRepository;
import com.devsocial.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final int SEARCH_PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final BlockRepository blockRepository;
    private final BlockService blockService;
    private final MediaStorageService storage;
    private final MediaUrlSigner signer;
    private final boolean requireVerifiedEmail;

    public UserService(UserRepository userRepository, FollowRepository followRepository, PostRepository postRepository,
                       BlockRepository blockRepository, BlockService blockService, MediaStorageService storage,
                       MediaUrlSigner signer, @Value("${app.auth.require-verified-email}") boolean requireVerifiedEmail) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.blockRepository = blockRepository;
        this.blockService = blockService;
        this.storage = storage;
        this.signer = signer;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public User getEntity(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
    }

    public boolean emailVerificationRequired() {
        return requireVerifiedEmail;
    }

    /** Posting, commenting, liking, following and reporting need a confirmed email address (anti-spam). */
    public void requireVerified(User user) {
        if (requireVerifiedEmail && !user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Please confirm your email address first. Check your inbox, or resend the link from Settings.");
        }
    }

    /** Compact author info for posts and comments, with a signed photo link. */
    public PostResponse.Author author(User u) {
        return new PostResponse.Author(u.getId(), u.getUsername(), u.getNameToShow(), signer.sign(u.getAvatarUrl()));
    }

    /** Maps a user as seen by {@code viewer} (may be null). */
    public UserResponse toResponse(User u, User viewer) {
        return toResponses(List.of(u), viewer).get(0);
    }

    /** Batch mapping: a handful of queries total, however many users. */
    public List<UserResponse> toResponses(List<User> users, User viewer) {
        if (users.isEmpty()) return List.of();
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, Long> followers = toCountMap(followRepository.countFollowersByUserIds(ids));
        Map<Long, Long> following = toCountMap(followRepository.countFollowingByUserIds(ids));
        Map<Long, Long> posts = toCountMap(postRepository.countByAuthorIds(ids));
        Set<Long> followedByViewer = viewer == null ? Set.of() : followRepository.findFollowingIds(viewer.getId());
        Set<Long> blockedByViewer = blockService.blockedByMe(viewer);
        boolean viewerIsAdmin = viewer != null && viewer.isAdmin();
        return users.stream().map(u -> {
            boolean self = viewer != null && viewer.getId().equals(u.getId());
            return new UserResponse(u.getId(), u.getUsername(), u.getNameToShow(), u.getBio(), signer.sign(u.getAvatarUrl()),
                    u.getGithubUrl(), split(u.getSkills()), split(u.getInterests()),
                    followers.getOrDefault(u.getId(), 0L), following.getOrDefault(u.getId(), 0L), posts.getOrDefault(u.getId(), 0L),
                    !self && followedByViewer.contains(u.getId()), blockedByViewer.contains(u.getId()),
                    self ? u.isEmailVerified() : null, self ? u.isAdmin() : null,
                    viewerIsAdmin ? u.isSuspended() : null);
        }).toList();
    }

    /** Your own account, including private fields (email verified, moderator). */
    public UserResponse toSelfResponse(User u) {
        return toResponse(u, u);
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
    }

    /**
     * Whether {@code viewer} may see {@code target}'s profile and posts: not if the target blocked
     * the viewer, and not if the target is suspended (moderators still can).
     */
    public boolean canSee(User viewer, User target) {
        if (viewer.getId().equals(target.getId())) return true;
        if (target.isSuspended() && !viewer.isAdmin()) return false;
        return !blockService.blocked(target, viewer);
    }

    /** Like {@link #getEntity} but 404s for profiles the viewer isn't allowed to see. */
    public User getVisible(String username, User viewer) {
        User target = getEntity(username);
        if (!canSee(viewer, target)) throw new UserNotFoundException(username);
        return target;
    }

    @Transactional(readOnly = true)
    public UserResponse me(String username) {
        return toSelfResponse(getEntity(username));
    }

    @Transactional(readOnly = true)
    public UserResponse profile(String username, String viewerUsername) {
        User viewer = getEntity(viewerUsername);
        return toResponse(getVisible(username, viewer), viewer);
    }

    /** People search by username, display name or skill. Excludes you, blocked and suspended users. */
    @Transactional(readOnly = true)
    public Page<UserResponse> search(String viewerUsername, String query, int page) {
        User viewer = getEntity(viewerUsername);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.startsWith("@")) q = q.substring(1);
        if (q.length() > 50) q = q.substring(0, 50);
        String escaped = q.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        // A Slice fetches one extra row to know whether there's a next page.
        var found = userRepository.search("%" + escaped + "%", escaped + "%", viewer.getId(),
                blockService.hiddenIds(viewer), PageRequest.of(Math.max(0, page), SEARCH_PAGE_SIZE));
        return new Page<>(toResponses(found.getContent(), viewer), found.hasNext());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> suggestions(String viewerUsername, int limit) {
        User viewer = getEntity(viewerUsername);
        return toResponses(userRepository.suggestions(viewer.getId(), blockService.hiddenIds(viewer),
                PageRequest.of(0, Math.min(Math.max(limit, 1), 20))), viewer);
    }

    @Transactional
    public UserResponse update(String username, UpdateProfileRequest req) {
        User user = getEntity(username);
        if (req.displayName() != null) user.setDisplayName(cleanName(req.displayName()));
        if (req.bio() != null) user.setBio(req.bio().trim());
        if (req.githubUrl() != null) user.setGithubUrl(cleanUrl(req.githubUrl(), "githubUrl"));
        if (req.skills() != null) {
            List<String> skills = req.skills().stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.replace(",", " ").trim())
                    .filter(s -> !s.isEmpty() && s.length() <= 30)
                    .distinct().limit(15).toList();
            user.setSkills(String.join(",", skills));
        }
        if (req.interests() != null) {
            for (String i : req.interests()) {
                // List.of(...).contains(null) throws, so check null explicitly.
                if (i == null || !Categories.ALL.contains(i)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown interest: " + i);
                }
            }
            user.setInterests(String.join(",", req.interests().stream().distinct().toList()));
        }
        userRepository.save(user);
        return toSelfResponse(user);
    }

    /** Trims, collapses whitespace and drops control characters; blank means "no display name". */
    public static String cleanName(String name) {
        if (name == null) return null;
        String v = name.replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").trim();
        if (v.length() > 50) v = v.substring(0, 50).trim();
        return v.isEmpty() ? null : v;
    }

    /** Replaces the profile photo with an uploaded image; the old upload is deleted after commit. */
    @Transactional
    public UserResponse updateAvatar(String username, MultipartFile image) {
        User user = getEntity(username);
        String oldKey = MediaStorageService.keyFromUrl(user.getAvatarUrl());
        user.setAvatarUrl(MediaStorageService.urlFor(storage.store(image)));
        userRepository.save(user);
        storage.deleteAfterCommit(oldKey);
        return toSelfResponse(user);
    }

    @Transactional
    public UserResponse removeAvatar(String username) {
        User user = getEntity(username);
        storage.deleteAfterCommit(MediaStorageService.keyFromUrl(user.getAvatarUrl()));
        user.setAvatarUrl(null);
        userRepository.save(user);
        return toSelfResponse(user);
    }

    @Transactional
    public UserResponse block(String viewerUsername, String targetUsername) {
        User viewer = getEntity(viewerUsername);
        User target = getEntity(targetUsername);
        blockService.block(viewer, target);
        return toResponse(target, viewer);
    }

    @Transactional
    public UserResponse unblock(String viewerUsername, String targetUsername) {
        User viewer = getEntity(viewerUsername);
        User target = getEntity(targetUsername);
        blockService.unblock(viewer, target);
        return toResponse(target, viewer);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> blockedUsers(String viewerUsername) {
        User viewer = getEntity(viewerUsername);
        List<User> blocked = blockRepository.findByBlockerIdOrderByCreatedAtDesc(viewer.getId()).stream()
                .map(Block::getBlocked).toList();
        return toResponses(blocked, viewer);
    }

    private String cleanUrl(String url, String field) {
        String v = url.trim();
        if (v.isEmpty()) return null;
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must start with http:// or https://");
        }
        return v;
    }
}
