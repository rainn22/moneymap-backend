package com.example.moneymap.config;

import com.example.moneymap.features.user.entity.Role;
import com.example.moneymap.features.user.entity.User;
import com.example.moneymap.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner adminBootstrapRunner(
            @Value("${app.admin.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.admin.username:admin}") String username,
            @Value("${app.admin.email:admin@moneymap.com}") String email,
            @Value("${app.admin.password:}") String password,
            @Value("${app.admin.first-name:System}") String firstName,
            @Value("${app.admin.last-name:Admin}") String lastName) {
        return args -> {
            if (!enabled) {
                log.info("Admin bootstrap is disabled.");
                return;
            }

            if (password == null || password.isBlank()) {
                log.warn("Admin bootstrap is enabled but no password was provided. Skipping admin creation.");
                return;
            }

            if (userRepository.existsByEmail(email)) {
                log.info("Admin bootstrap skipped because account already exists email={}", email);
                return;
            }

            User adminUser = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .firstName(firstName)
                    .lastName(lastName)
                    .enabled(true)
                    .role(Role.ADMIN)
                    .build();

            userRepository.save(adminUser);
            log.info("Bootstrapped admin account email={} username={}", email, username);
        };
    }
}
