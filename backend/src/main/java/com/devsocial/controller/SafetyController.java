package com.devsocial.controller;

import com.devsocial.dto.AccountRequests.*;
import com.devsocial.dto.UserResponse;
import com.devsocial.service.ModerationService;
import com.devsocial.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Reporting (any signed-in user) and moderation (/api/admin/**: moderators only, see SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private final ReportService reportService;
    private final ModerationService moderationService;

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse report(@Valid @RequestBody ReportRequest req, Principal principal) {
        reportService.create(principal.getName(), req);
        return new MessageResponse("Thanks for letting us know. A moderator will review it.");
    }

    @GetMapping("/api/admin/reports")
    public List<ReportResponse> reports(@RequestParam(defaultValue = "open") String status) {
        return moderationService.reports(!"closed".equalsIgnoreCase(status));
    }

    @GetMapping("/api/admin/reports/count")
    public Map<String, Long> openCount() {
        return Map.of("open", moderationService.openCount());
    }

    @PostMapping("/api/admin/reports/{id}/resolve")
    public ReportResponse resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest req, Principal principal) {
        return moderationService.resolve(id, req.action(), principal.getName());
    }

    @PostMapping("/api/admin/users/{username}/suspend")
    public UserResponse suspend(@PathVariable String username, Principal principal) {
        return moderationService.setSuspended(username, true, principal.getName());
    }

    @DeleteMapping("/api/admin/users/{username}/suspend")
    public UserResponse unsuspend(@PathVariable String username, Principal principal) {
        return moderationService.setSuspended(username, false, principal.getName());
    }
}
