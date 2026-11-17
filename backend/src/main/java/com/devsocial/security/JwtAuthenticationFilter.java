package com.devsocial.security;

import com.devsocial.model.User;
import com.devsocial.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Signs the request in from its Bearer token. The token is ignored (request stays anonymous, so
 * protected endpoints answer 401) when the account no longer exists, is suspended, or changed its
 * password after the token was issued (password reset signs out every other session).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(header.substring(7))
                    .flatMap(claims -> userRepository.findByUsername(claims.username())
                            .filter(user -> !user.isSuspended() && !issuedBeforePasswordChange(claims.issuedAt(), user)))
                    .ifPresent(user -> {
                        var authorities = user.isAdmin()
                                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                        var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    /** JWT times have whole-second precision, so compare at that precision. */
    private static boolean issuedBeforePasswordChange(Instant issuedAt, User user) {
        Instant changed = user.getCredentialsChangedAt();
        return changed != null && issuedAt.isBefore(changed.truncatedTo(ChronoUnit.SECONDS));
    }
}
