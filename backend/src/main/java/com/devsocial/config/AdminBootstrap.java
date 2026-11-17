package com.devsocial.config;

import com.devsocial.repository.UserRepository;
import com.devsocial.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, makes already-verified accounts listed in ADMIN_EMAILS moderators.
 * (Accounts that verify later are promoted at that moment, see AuthService.)
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthService authService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : authService.adminEmails()) {
            userRepository.findByEmail(email).filter(u -> u.isEmailVerified() && !u.isAdmin()).ifPresent(u -> {
                authService.promoteIfAdmin(u);
                userRepository.save(u);
                log.info("Promoted @{} to moderator (listed in ADMIN_EMAILS)", u.getUsername());
            });
        }
    }
}
