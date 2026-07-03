package com.pixierge.api.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    public static final Duration SESSION_DURATION = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            SessionTokenService sessionTokenService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
    }

    public boolean setupRequired() {
        return userRepository.countUsers() == 0;
    }

    @Transactional
    public CreatedSession createFirstAdmin(CreateAdminRequest request) {
        userRepository.lockFirstAdminSetup();
        if (userRepository.countUsers() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Initial admin already exists");
        }

        ValidatedAccountInput input = validateAccountInput(request.email(), request.displayName(), request.password());
        UUID userId = userRepository.createUser(input.email(), input.displayName(), passwordEncoder.encode(input.password()));
        userRepository.assignRole(userId, IdentityConstants.ROLE_ADMIN);

        return createSessionForUser(userId);
    }

    @Transactional
    public CreatedSession login(LoginRequest request) {
        String email = UserRepository.normalizeEmail(request.email());
        String password = request.password() == null ? "" : request.password();

        UserRepository.LoginCredential credential = userRepository.findLoginCredential(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return createSessionForUser(credential.userId());
    }

    public Optional<AuthenticatedUser> authenticateSession(String sessionToken) {
        String tokenHash = sessionTokenService.sha256(sessionToken);
        return sessionRepository.findActiveByTokenHash(tokenHash)
                .flatMap(session -> userRepository.findAuthenticatedUser(session.userId(), session.csrfToken()));
    }

    public void logout(String sessionToken) {
        sessionRepository.revokeByTokenHash(sessionTokenService.sha256(sessionToken));
    }

    private CreatedSession createSessionForUser(UUID userId) {
        String sessionToken = sessionTokenService.newToken();
        String csrfToken = sessionTokenService.newToken();
        sessionRepository.createSession(
                userId,
                sessionTokenService.sha256(sessionToken),
                csrfToken,
                OffsetDateTime.now().plus(SESSION_DURATION)
        );

        AuthenticatedUser user = userRepository.findAuthenticatedUser(userId, csrfToken)
                .orElseThrow(() -> new IllegalStateException("Created session for missing user"));

        return new CreatedSession(sessionToken, user);
    }

    private ValidatedAccountInput validateAccountInput(String email, String displayName, String password) {
        String normalizedEmail = UserRepository.normalizeEmail(email);
        String cleanDisplayName = displayName == null ? "" : displayName.trim();
        String cleanPassword = password == null ? "" : password;

        if (!normalizedEmail.contains("@") || normalizedEmail.length() > 320) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (cleanDisplayName.isBlank() || cleanDisplayName.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        if (cleanPassword.length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters");
        }

        return new ValidatedAccountInput(normalizedEmail, cleanDisplayName, cleanPassword);
    }

    record CreatedSession(String sessionToken, AuthenticatedUser user) {
    }

    private record ValidatedAccountInput(String email, String displayName, String password) {
    }
}
