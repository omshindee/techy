package com.devsocial.controller;

import com.devsocial.dto.CommentRequest;
import com.devsocial.dto.CommentResponse;
import com.devsocial.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> list(@PathVariable Long postId, Principal principal) {
        return commentService.list(postId, principal.getName());
    }

    /** By the comment's author, the post's author, or a moderator. */
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long postId, @PathVariable Long commentId, Principal principal) {
        commentService.delete(postId, commentId, principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@PathVariable Long postId, @Valid @RequestBody CommentRequest req, Principal principal) {
        return commentService.add(postId, principal.getName(), req);
    }
}
