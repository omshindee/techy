package com.devsocial.controller;

import com.devsocial.dto.AccountRequests.MessageResponse;
import com.devsocial.dto.AccountRequests.Page;
import com.devsocial.dto.UpdateProfileRequest;
import com.devsocial.dto.UserResponse;
import com.devsocial.service.AuthService;
import com.devsocial.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /** People search by username, display name or skill; 20 per page. Empty q lists everyone. */
    @GetMapping
    public Page<UserResponse> search(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page,
                                     Principal principal) {
        return userService.search(principal.getName(), q, page);
    }

    /** People you don't follow yet (for "Developers to follow"). */
    @GetMapping("/suggestions")
    public List<UserResponse> suggestions(@RequestParam(defaultValue = "5") int limit, Principal principal) {
        return userService.suggestions(principal.getName(), limit);
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return userService.me(principal.getName());
    }

    @PutMapping("/me")
    public UserResponse update(Principal principal, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.update(principal.getName(), req);
    }

    /** Upload a new profile photo (multipart field "image"). */
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(Principal principal, @RequestPart MultipartFile image) {
        return userService.updateAvatar(principal.getName(), image);
    }

    @DeleteMapping("/me/avatar")
    public UserResponse removeAvatar(Principal principal) {
        return userService.removeAvatar(principal.getName());
    }

    /** Sends a fresh email-confirmation link. */
    @PostMapping("/me/verification")
    public MessageResponse resendVerification(Principal principal) {
        authService.resendVerification(principal.getName());
        return new MessageResponse("We sent a new confirmation link. Check your inbox.");
    }

    @GetMapping("/me/blocked")
    public List<UserResponse> blocked(Principal principal) {
        return userService.blockedUsers(principal.getName());
    }

    @GetMapping("/{username}")
    public UserResponse profile(@PathVariable String username, Principal principal) {
        return userService.profile(username, principal.getName());
    }

    @PostMapping("/{username}/block")
    public UserResponse block(@PathVariable String username, Principal principal) {
        return userService.block(principal.getName(), username);
    }

    @DeleteMapping("/{username}/block")
    public UserResponse unblock(@PathVariable String username, Principal principal) {
        return userService.unblock(principal.getName(), username);
    }
}
