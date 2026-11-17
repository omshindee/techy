package com.devsocial.controller;

import com.devsocial.dto.UserResponse;
import com.devsocial.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users/{username}")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/follow")
    public UserResponse follow(@PathVariable String username, Principal principal) {
        return followService.follow(principal.getName(), username);
    }

    @DeleteMapping("/follow")
    public UserResponse unfollow(@PathVariable String username, Principal principal) {
        return followService.unfollow(principal.getName(), username);
    }

    @GetMapping("/followers")
    public List<UserResponse> followers(@PathVariable String username, Principal principal) {
        return followService.followers(username, principal.getName());
    }

    @GetMapping("/following")
    public List<UserResponse> following(@PathVariable String username, Principal principal) {
        return followService.following(username, principal.getName());
    }
}
