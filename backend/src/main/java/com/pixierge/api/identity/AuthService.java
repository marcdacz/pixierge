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

        ValidatedAccountInput input = validateAccountInput(request.username(), request.password());
        UUID userId = userRepository.createUser(input.username(), passwordEncoder.encode(input.password()));
        userRepository.assignRole(userId, IdentityConstants.ROLE_ADMIN);

        return createSessionForUser(userId);
    }

    @Transactional
    public CreatedSession login(LoginRequest request) {
        String username = UserRepository.normalizeUsername(request.username());
        String password = request.password() == null ? "" : request.password();

        UserRepository.LoginCredential credential = userRepository.findLoginCredential(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
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

    private ValidatedAccountInput validateAccountInput(String username, String password) {
        String normalizedUsername = UserRepository.normalizeUsername(username);
        String cleanPassword = password == null ? "" : password;

        if (normalizedUsername.isBlank() || normalizedUsername.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (!normalizedUsername.matches("[a-z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username may only contain letters, numbers, dots, underscores, and hyphens");
        }
        if (cleanPassword.length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters");
        }

        return new ValidatedAccountInput(normalizedUsername, cleanPassword);
    }

    record CreatedSession(String sessionToken, AuthenticatedUser user) {
    }

    private record ValidatedAccountInput(String username, String password) {
    }
}
