package com.devsocial.service;

import com.devsocial.dto.AccountRequests.AuthConfig;
import com.devsocial.dto.AuthResponse;
import com.devsocial.dto.LoginRequest;
import com.devsocial.dto.RegisterRequest;
import com.devsocial.exception.ApiException;
import com.devsocial.model.EmailToken;
import com.devsocial.model.User;
import com.devsocial.repository.UserRepository;
import com.devsocial.security.CaptchaVerifier;
import com.devsocial.security.JwtService;
import com.devsocial.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Duration VERIFY_LINK_VALID = Duration.ofHours(48);
    private static final Duration RESET_LINK_VALID = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;
    private final EmailTokenService tokens;
    private final MailService mail;
    private final RateLimiter rateLimiter;
    private final CaptchaVerifier captcha;
    private final Set<String> adminEmails;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       UserService userService, EmailTokenService tokens, MailService mail, RateLimiter rateLimiter,
                       CaptchaVerifier captcha, @Value("${app.admin.emails:}") String adminEmails) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userService = userService;
        this.tokens = tokens;
        this.mail = mail;
        this.rateLimiter = rateLimiter;
        this.captcha = captcha;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public AuthConfig config() {
        return new AuthConfig(captcha.siteKey(), userService.emailVerificationRequired());
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, String ip) {
        rateLimiter.check("register-ip", ip, 5, Duration.ofHours(1));
        // Honeypot: the "website" field is invisible to people, so only bots fill it in.
        if (req.website() != null && !req.website().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sign-up failed. Please try again.");
        }
        captcha.verify(req.captchaToken(), ip);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        // Case-insensitive so "Alice" can't be registered next to "alice".
        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setDisplayName(UserService.cleanName(req.displayName()));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        userRepository.save(user);
        sendVerification(user);
        return new AuthResponse(jwtService.generateToken(user.getUsername()), userService.toSelfResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req, String ip) {
        String id = req.username().trim();
        rateLimiter.check("login-ip", ip, 20, Duration.ofMinutes(5));
        rateLimiter.check("login-account", id, 10, Duration.ofMinutes(15)); // slows password guessing on one account
        // Exact username first, then a unique case-insensitive match, then email.
        User user = userRepository.findByUsername(id)
                .or(() -> {
                    List<User> matches = userRepository.findByUsernameIgnoreCase(id);
                    return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
                })
                .or(() -> userRepository.findByEmail(id.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (user.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account has been suspended for breaking the community rules.");
        }
        return new AuthResponse(jwtService.generateToken(user.getUsername()), userService.toSelfResponse(user));
    }

    /** Emails a confirmation link. Called on sign-up and when the user asks for a new link. */
    private void sendVerification(User user) {
        String token = tokens.issue(user, EmailToken.Purpose.VERIFY_EMAIL, VERIFY_LINK_VALID);
        mail.send(user.getEmail(), "Confirm your email for Techy", """
                Hi %s,

                Welcome to Techy! Confirm your email address to start posting, commenting and following people:

                %s

                The link works for 48 hours. If you didn't create a Techy account, you can ignore this email.
                """.formatted(user.getNameToShow(), mail.link("/verify-email?token=" + token)));
    }

    @Transactional
    public void resendVerification(String username) {
        User user = userService.getEntity(username);
        if (user.isEmailVerified()) throw new ApiException(HttpStatus.BAD_REQUEST, "Your email is already confirmed.");
        rateLimiter.check("resend-verify", username, 3, Duration.ofHours(1));
        sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = tokens.redeem(token, EmailToken.Purpose.VERIFY_EMAIL);
        user.setEmailVerified(true);
        promoteIfAdmin(user);
        userRepository.save(user);
    }

    /**
     * Always answers the same way, whether or not the email has an account, so the form can't be
     * used to find out who is registered.
     */
    @Transactional
    public void forgotPassword(String email, String captchaToken, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        rateLimiter.check("forgot-ip", ip, 5, Duration.ofHours(1));
        rateLimiter.check("forgot-email", normalized, 3, Duration.ofHours(1));
        captcha.verify(captchaToken, ip);
        userRepository.findByEmail(normalized).filter(u -> !u.isSuspended()).ifPresent(user -> {
            String token = tokens.issue(user, EmailToken.Purpose.RESET_PASSWORD, RESET_LINK_VALID);
            mail.send(user.getEmail(), "Reset your Techy password", """
                    Hi %s,

                    Someone (hopefully you) asked to reset the password for your Techy account @%s.
                    Choose a new password here:

                    %s

                    The link works for 1 hour and only once. If you didn't ask for this, ignore this email;
                    your password stays the same.
                    """.formatted(user.getNameToShow(), user.getUsername(), mail.link("/reset-password?token=" + token)));
        });
    }

    /** Sets the new password, signs out every existing session, and signs this one in. */
    @Transactional
    public AuthResponse resetPassword(String token, String newPassword) {
        User user = tokens.redeem(token, EmailToken.Purpose.RESET_PASSWORD);
        if (user.isSuspended()) throw new ApiException(HttpStatus.FORBIDDEN, "This account has been suspended.");
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setCredentialsChangedAt(Instant.now());
        user.setEmailVerified(true); // they just proved they can read this inbox
        promoteIfAdmin(user);
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user.getUsername()), userService.toSelfResponse(user));
    }

    /** Accounts whose verified email is listed in ADMIN_EMAILS become moderators. */
    public void promoteIfAdmin(User user) {
        if (user.isEmailVerified() && adminEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
            user.setRole(User.ROLE_ADMIN);
        }
    }

    public Set<String> adminEmails() {
        return adminEmails;
    }
}
