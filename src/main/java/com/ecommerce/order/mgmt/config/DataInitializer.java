package com.ecommerce.order.mgmt.config;

import com.ecommerce.order.mgmt.entity.User;
import com.ecommerce.order.mgmt.enums.Role;
import com.ecommerce.order.mgmt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Resolved from application.properties → .env env vars
    @Value("${app.seed.admin.username}")
    private String adminUsername;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.user.username}")
    private String userUsername;

    @Value("${app.seed.user.password}")
    private String userPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = Objects.requireNonNull(User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .email("admin@example.com")
                    .role(Role.ADMIN)
                    .build());
            userRepository.save(admin);
            log.info("Seeded admin user ({})", adminUsername);
        }

        if (!userRepository.existsByUsername(userUsername)) {
            User regular = Objects.requireNonNull(User.builder()
                    .username(userUsername)
                    .password(passwordEncoder.encode(userPassword))
                    .email("user@example.com")
                    .role(Role.USER)
                    .build());
            userRepository.save(regular);
            log.info("Seeded regular user ({})", userUsername);
        }
    }
}
