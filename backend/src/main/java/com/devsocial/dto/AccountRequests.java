package com.devsocial.dto;

import com.devsocial.model.Report;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Small request/response records for accounts, safety and moderation. */
public final class AccountRequests {
    private AccountRequests() {}

    /** What the sign-up page needs to know before rendering. */
    public record AuthConfig(String captchaSiteKey, boolean emailVerificationRequired) {}

    public record TokenRequest(@NotBlank String token) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email, String captchaToken) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String password) {}

    public record MessageResponse(String message) {}

    public record Page<T>(List<T> items, boolean hasMore) {}

    public record ReportRequest(@NotNull Report.TargetType targetType, @NotNull Long targetId,
                                @NotNull Report.Reason reason, @Size(max = 500) String details) {}

    /** Moderator decision on a report: DISMISS, REMOVE (the post/comment) or SUSPEND (the author / account). */
    public enum ModerationAction { DISMISS, REMOVE, SUSPEND }

    public record ResolveRequest(@NotNull ModerationAction action) {}

    /** What was reported, as the moderator sees it. {@code exists} is false once it's been deleted. */
    public record ReportTarget(boolean exists, PostResponse.Author author, String text, String imageUrl,
                               Long postId, boolean authorSuspended) {}

    public record ReportResponse(Long id, Report.TargetType targetType, Long targetId, Report.Reason reason,
                                 String details, Report.Status status, String resolution,
                                 java.time.Instant createdAt, java.time.Instant resolvedAt,
                                 PostResponse.Author reporter, String resolvedBy, ReportTarget target) {}
}
