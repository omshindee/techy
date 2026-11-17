package com.devsocial.controller;

import com.devsocial.dto.AccountRequests.*;
import com.devsocial.dto.AuthResponse;
import com.devsocial.dto.LoginRequest;
import com.devsocial.dto.RegisterRequest;
import com.devsocial.security.ClientIp;
import com.devsocial.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Public endpoints: no login token needed. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIp clientIp;

    /** Captcha site key (if enabled) and whether email confirmation is required. */
    @GetMapping("/config")
    public AuthConfig config() {
        return authService.config();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return authService.register(req, clientIp.of(http));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, clientIp.of(http));
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest req) {
        authService.verifyEmail(req.token());
        return new MessageResponse("Your email is confirmed. You're all set.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        authService.forgotPassword(req.email(), req.captchaToken(), clientIp.of(http));
        return new MessageResponse("If an account uses that email, we've sent a link to reset the password. Check your inbox.");
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req.token(), req.password());
    }
}
