package com.devsocial.controller;

import com.devsocial.dto.LikeResponse;
import com.devsocial.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/posts/{postId}/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public LikeResponse like(@PathVariable Long postId, Principal principal) {
        try {
            return likeService.like(postId, principal.getName());
        } catch (DataIntegrityViolationException e) {
            // A concurrent request (e.g. a double click) inserted the same like first.
            // The like exists, so report the current state instead of a 409.
            return likeService.state(postId, principal.getName());
        }
    }

    @DeleteMapping
    public LikeResponse unlike(@PathVariable Long postId, Principal principal) {
        return likeService.unlike(postId, principal.getName());
    }
}
