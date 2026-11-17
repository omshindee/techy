package com.devsocial.controller;

import com.devsocial.dto.MessageDtos.*;
import com.devsocial.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Direct messages between people (see MessageService for who can message whom). */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public List<ConversationSummary> inbox(Principal principal) {
        return messageService.inbox(principal.getName());
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(Principal principal) {
        return new UnreadCount(messageService.unreadCount(principal.getName()));
    }

    /** The chat with a person. {@code after}: only newer messages (polling); {@code before}: older page. */
    @GetMapping("/with/{username}")
    public ThreadResponse thread(@PathVariable String username,
                                 @RequestParam(required = false) Long after,
                                 @RequestParam(required = false) Long before,
                                 Principal principal) {
        return messageService.thread(principal.getName(), username, after, before);
    }

    @PostMapping("/with/{username}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@PathVariable String username, @Valid @RequestBody SendMessageRequest req, Principal principal) {
        return messageService.send(principal.getName(), username, req.content());
    }
}
