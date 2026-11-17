package com.devsocial.service;

import com.devsocial.dto.AccountRequests.ModerationAction;
import com.devsocial.dto.AccountRequests.ReportResponse;
import com.devsocial.dto.AccountRequests.ReportTarget;
import com.devsocial.dto.UserResponse;
import com.devsocial.exception.ApiException;
import com.devsocial.model.Comment;
import com.devsocial.model.Post;
import com.devsocial.model.Report;
import com.devsocial.model.User;
import com.devsocial.repository.CommentRepository;
import com.devsocial.repository.PostRepository;
import com.devsocial.repository.ReportRepository;
import com.devsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Moderator actions. Only reachable through /api/admin/** (ADMIN role, see SecurityConfig). */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final com.devsocial.repository.MessageRepository messageRepository;
    private final PostService postService;
    private final ReportService reportService;
    private final UserService userService;
    private final MediaUrlSigner signer;

    @Transactional(readOnly = true)
    public List<ReportResponse> reports(boolean open) {
        List<Report> reports = open
                ? reportRepository.findByStatusOrderByCreatedAtDesc(Report.Status.OPEN, PageRequest.of(0, 100))
                : reportRepository.findByStatusNotOrderByResolvedAtDesc(Report.Status.OPEN, PageRequest.of(0, 100));
        return reports.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return reportRepository.countByStatus(Report.Status.OPEN);
    }

    @Transactional
    public ReportResponse resolve(Long reportId, ModerationAction action, String moderatorUsername) {
        User moderator = userService.getEntity(moderatorUsername);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getStatus() != Report.Status.OPEN) throw new ApiException(HttpStatus.CONFLICT, "This report was already handled");
        String resolution;
        switch (action) {
            case DISMISS -> {
                close(List.of(report), Report.Status.DISMISSED, "Dismissed", moderator);
                return toResponse(report);
            }
            case REMOVE -> {
                switch (report.getTargetType()) {
                    case POST -> postRepository.findById(report.getTargetId()).ifPresent(postService::remove);
                    case COMMENT -> commentRepository.findById(report.getTargetId()).ifPresent(commentRepository::delete);
                    case MESSAGE -> messageRepository.findById(report.getTargetId()).ifPresent(messageRepository::delete);
                    case USER -> throw new ApiException(HttpStatus.BAD_REQUEST, "To act on an account, suspend it");
                }
                resolution = "Removed " + report.getTargetType().name().toLowerCase();
            }
            case SUSPEND -> {
                User owner = reportService.ownerOf(report.getTargetType(), report.getTargetId());
                suspend(owner, moderator);
                resolution = "Suspended @" + owner.getUsername();
            }
            default -> throw new IllegalStateException();
        }
        // Everyone else's open reports about the same thing are settled by the same decision.
        close(reportRepository.findByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(), Report.Status.OPEN),
                Report.Status.RESOLVED, resolution, moderator);
        return toResponse(report);
    }

    @Transactional
    public UserResponse setSuspended(String username, boolean suspended, String moderatorUsername) {
        User moderator = userService.getEntity(moderatorUsername);
        User target = userService.getEntity(username);
        if (suspended) suspend(target, moderator);
        else target.setSuspended(false);
        userRepository.save(target);
        return userService.toResponse(target, moderator);
    }

    private void suspend(User target, User moderator) {
        if (target.getId().equals(moderator.getId())) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't suspend yourself");
        if (target.isAdmin()) throw new ApiException(HttpStatus.BAD_REQUEST, "Moderators can't be suspended here");
        target.setSuspended(true); // also signs them out: the JWT filter ignores suspended accounts
        userRepository.save(target);
    }

    private void close(List<Report> reports, Report.Status status, String resolution, User moderator) {
        Instant now = Instant.now();
        for (Report r : reports) {
            r.setStatus(status);
            r.setResolution(resolution);
            r.setResolvedBy(moderator);
            r.setResolvedAt(now);
        }
        reportRepository.saveAll(reports);
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(r.getId(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getDetails(),
                r.getStatus(), r.getResolution(), r.getCreatedAt(), r.getResolvedAt(),
                userService.author(r.getReporter()), r.getResolvedBy() == null ? null : r.getResolvedBy().getUsername(),
                target(r));
    }

    private ReportTarget target(Report r) {
        return switch (r.getTargetType()) {
            case POST -> {
                Optional<Post> p = postRepository.findById(r.getTargetId());
                yield p.map(post -> new ReportTarget(true, userService.author(post.getAuthor()), post.getContent(),
                                signer.sign(MediaStorageService.urlFor(post.getImageKey())), post.getId(), post.getAuthor().isSuspended()))
                        .orElse(gone());
            }
            case COMMENT -> {
                Optional<Comment> c = commentRepository.findById(r.getTargetId());
                yield c.map(comment -> new ReportTarget(true, userService.author(comment.getAuthor()), comment.getContent(),
                                null, comment.getPost().getId(), comment.getAuthor().isSuspended()))
                        .orElse(gone());
            }
            case USER -> {
                Optional<User> u = userRepository.findById(r.getTargetId());
                yield u.map(user -> new ReportTarget(true, userService.author(user), user.getBio(), null, null, user.isSuspended()))
                        .orElse(gone());
            }
            // Only the reported message is shown to moderators, never the rest of the private conversation.
            case MESSAGE -> messageRepository.findById(r.getTargetId())
                    .map(m -> new ReportTarget(true, userService.author(m.getSender()), m.getContent(), null, null, m.getSender().isSuspended()))
                    .orElse(gone());
        };
    }

    private static ReportTarget gone() {
        return new ReportTarget(false, null, null, null, null, false);
    }
}
