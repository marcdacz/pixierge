package com.pixierge.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "pixierge.test-seed.enabled", havingValue = "true")
class TestAdminSeeder implements ApplicationRunner {

    private final PasswordEncoder passwordEncoder;
    private final String password;
    private final UserRepository userRepository;
    private final String username;

    TestAdminSeeder(
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            @Value("${pixierge.test-seed.admin-username:admindacz}") String username,
            @Value("${pixierge.test-seed.admin-password:admindacz123}") String password
    ) {
        this.passwordEncoder = passwordEncoder;
        this.password = password;
        this.userRepository = userRepository;
        this.username = username;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findLoginCredential(username).isPresent()) {
            return;
        }

        UUID userId = userRepository.createUser(username, passwordEncoder.encode(password));
        userRepository.assignRole(userId, IdentityConstants.ROLE_ADMIN);
    }
}
