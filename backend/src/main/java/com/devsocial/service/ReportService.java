package com.devsocial.service;

import com.devsocial.dto.AccountRequests.ReportRequest;
import com.devsocial.exception.ApiException;
import com.devsocial.model.Comment;
import com.devsocial.model.Message;
import com.devsocial.model.Post;
import com.devsocial.model.Report;
import com.devsocial.model.User;
import com.devsocial.repository.CommentRepository;
import com.devsocial.repository.MessageRepository;
import com.devsocial.repository.PostRepository;
import com.devsocial.repository.ReportRepository;
import com.devsocial.repository.UserRepository;
import com.devsocial.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Users flag posts, comments or accounts; moderators review them on the Moderation page. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    @Transactional
    public void create(String reporterUsername, ReportRequest req) {
        User reporter = userService.getEntity(reporterUsername);
        userService.requireVerified(reporter);
        User owner = ownerOf(req.targetType(), req.targetId());
        if (owner.getId().equals(reporter.getId())) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't report your own content");
        if (req.targetType() == Report.TargetType.MESSAGE) {
            // Messages are private: only the other person in that conversation can report one.
            boolean participant = messageRepository.findById(req.targetId())
                    .map(m -> m.getConversation().includes(reporter)).orElse(false);
            if (!participant) throw new ApiException(HttpStatus.NOT_FOUND, "Message not found");
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporter.getId(), req.targetType(), req.targetId())) {
            throw new ApiException(HttpStatus.CONFLICT, "You've already reported this. Our moderators will take a look.");
        }
        rateLimiter.check("reports", reporterUsername, 20, Duration.ofDays(1));
        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(req.targetType());
        report.setTargetId(req.targetId());
        report.setReason(req.reason());
        report.setDetails(req.details() == null || req.details().isBlank() ? null : req.details().trim());
        reportRepository.save(report);
    }

    /** The account behind a reported post, comment or user (404 if it doesn't exist). */
    public User ownerOf(Report.TargetType type, Long id) {
        return switch (type) {
            case POST -> postRepository.findById(id).map(Post::getAuthor)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Post not found"));
            case COMMENT -> commentRepository.findById(id).map(Comment::getAuthor)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
            case USER -> userRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
            case MESSAGE -> messageRepository.findById(id).map(Message::getSender)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        };
    }
}
