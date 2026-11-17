package com.devsocial.controller;

import com.devsocial.dto.NotificationDtos.NotificationPage;
import com.devsocial.dto.NotificationDtos.UnreadCount;
import com.devsocial.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** Your notifications: new followers, likes and comments on your posts. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Newest first; pass {@code before} (the previous page's {@code nextBefore}) for older ones. */
    @GetMapping
    public NotificationPage list(@RequestParam(required = false) Long before, Principal principal) {
        return notificationService.list(principal.getName(), before);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(Principal principal) {
        return new UnreadCount(notificationService.unreadCount(principal.getName()));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Principal principal) {
        notificationService.markAllRead(principal.getName());
    }
}
