package com.pixierge.api.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUserServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REPLACEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-07-30T00:00:00Z");

    private final StubUserRepository userRepository = new StubUserRepository();
    private final StubSessionRepository sessionRepository = new StubSessionRepository();
    private final AdminUserService service = new AdminUserService(userRepository, sessionRepository, new PrefixPasswordEncoder());

    @Test
    void createNormalizesPasswordAndAssignsStandardUserRole() {
        UserSummaryResponse response = service.create("  SAM  ", "a secure password");

        assertThat(response.username()).isEqualTo("sam");
        assertThat(userRepository.createdUsername).isEqualTo("sam");
        assertThat(userRepository.createdPasswordHash).isEqualTo("hash:a secure password");
        assertThat(userRepository.assignedRole).isEqualTo(IdentityConstants.ROLE_USER);
    }

    @Test
    void createRejectsDuplicateUsernames() {
        userRepository.usernameExists = true;

        assertThatThrownBy(() -> service.create("sam", "a secure password"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(userRepository.createdUsername).isNull();
    }

    @Test
    void resetPasswordStoresHashAndRevokesSessions() {
        userRepository.users.put(USER_ID, user(USER_ID, "sam", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));

        service.resetPassword(USER_ID, "a different secure password");

        assertThat(userRepository.updatedPasswordHash).isEqualTo("hash:a different secure password");
        assertThat(sessionRepository.revokedUserId).isEqualTo(USER_ID);
    }

    @Test
    void deactivationRejectsTheLastActiveAdministrator() {
        userRepository.users.put(USER_ID, user(USER_ID, "admin", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_ADMIN));
        userRepository.lastActiveAdmin = true;

        assertThatThrownBy(() -> service.updateStatus(USER_ID, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(userRepository.updatedStatus).isNull();
    }

    @Test
    void deactivationRevokesSessions() {
        userRepository.users.put(USER_ID, user(USER_ID, "sam", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));

        UserSummaryResponse response = service.updateStatus(USER_ID, false);

        assertThat(response.status()).isEqualTo(IdentityConstants.USER_STATUS_DISABLED);
        assertThat(userRepository.updatedStatus).isEqualTo(IdentityConstants.USER_STATUS_DISABLED);
        assertThat(sessionRepository.revokedUserId).isEqualTo(USER_ID);
    }

    @Test
    void deleteRejectsActorSelfDeletion() {
        assertThatThrownBy(() -> service.delete(USER_ID, USER_ID, REPLACEMENT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(userRepository.deletedUserId).isNull();
    }

    @Test
    void deleteRejectsTargetAsReplacement() {
        assertThatThrownBy(() -> service.delete(ACTOR_ID, USER_ID, USER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(userRepository.deletedUserId).isNull();
    }

    @Test
    void deleteRejectsMissingReplacement() {
        assertThatThrownBy(() -> service.delete(ACTOR_ID, USER_ID, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(userRepository.deletedUserId).isNull();
    }

    @Test
    void deleteRejectsInactiveReplacement() {
        userRepository.users.put(USER_ID, user(USER_ID, "sam", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));
        userRepository.users.put(REPLACEMENT_ID, user(REPLACEMENT_ID, "lee", IdentityConstants.USER_STATUS_DISABLED, IdentityConstants.ROLE_USER));

        assertThatThrownBy(() -> service.delete(ACTOR_ID, USER_ID, REPLACEMENT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(userRepository.transferredFrom).isNull();
    }

    @Test
    void deleteRejectsLastActiveAdminWhenReplacementIsNotAdmin() {
        userRepository.users.put(USER_ID, user(USER_ID, "admin", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_ADMIN));
        userRepository.users.put(REPLACEMENT_ID, user(REPLACEMENT_ID, "lee", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));
        userRepository.lastActiveAdmin = true;

        assertThatThrownBy(() -> service.delete(ACTOR_ID, USER_ID, REPLACEMENT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(userRepository.deletedUserId).isNull();
    }

    @Test
    void deleteTransfersOwnershipRevokesSessionsAndDeletesUser() {
        userRepository.users.put(USER_ID, user(USER_ID, "sam", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));
        userRepository.users.put(REPLACEMENT_ID, user(REPLACEMENT_ID, "lee", IdentityConstants.USER_STATUS_ACTIVE, IdentityConstants.ROLE_USER));

        service.delete(ACTOR_ID, USER_ID, REPLACEMENT_ID);

        assertThat(userRepository.transferredFrom).isEqualTo(USER_ID);
        assertThat(userRepository.transferredTo).isEqualTo(REPLACEMENT_ID);
        assertThat(sessionRepository.revokedUserId).isEqualTo(USER_ID);
        assertThat(userRepository.deletedUserId).isEqualTo(USER_ID);
    }

    private UserRepository.UserRecord user(UUID id, String username, String status, String role) {
        return new UserRepository.UserRecord(id, username, status, Set.of(role));
    }

    private static final class StubUserRepository extends UserRepository {
        private final Map<UUID, UserRecord> users = new HashMap<>();
        private boolean usernameExists;
        private boolean lastActiveAdmin;
        private String assignedRole;
        private String createdPasswordHash;
        private String createdUsername;
        private String updatedPasswordHash;
        private String updatedStatus;
        private UUID deletedUserId;
        private UUID transferredFrom;
        private UUID transferredTo;

        private StubUserRepository() {
            super(null);
        }

        @Override
        public boolean usernameExists(String username) {
            return usernameExists;
        }

        @Override
        public UUID createUser(String username, String passwordHash) {
            createdUsername = username;
            createdPasswordHash = passwordHash;
            users.put(USER_ID, new UserRecord(USER_ID, username, IdentityConstants.USER_STATUS_ACTIVE, Set.of(IdentityConstants.ROLE_USER)));
            return USER_ID;
        }

        @Override
        public void assignRole(UUID userId, String roleKey) {
            assignedRole = roleKey;
        }

        @Override
        public UserSummaryResponse requireUserSummary(UUID userId) {
            UserRecord user = users.get(userId);
            return new UserSummaryResponse(user.id(), user.username(), user.status(), user.roles(), CREATED_AT);
        }

        @Override
        public Optional<UserRecord> findUser(UUID userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public void updatePassword(UUID userId, String passwordHash) {
            updatedPasswordHash = passwordHash;
        }

        @Override
        public boolean isLastActiveAdmin(UUID userId) {
            return lastActiveAdmin;
        }

        @Override
        public void updateStatus(UUID userId, String status) {
            UserRecord user = users.get(userId);
            users.put(userId, new UserRecord(user.id(), user.username(), status, user.roles()));
            updatedStatus = status;
        }

        @Override
        public void transferOwnership(UUID userId, UUID replacementUserId) {
            transferredFrom = userId;
            transferredTo = replacementUserId;
        }

        @Override
        public void deleteUser(UUID userId) {
            deletedUserId = userId;
        }
    }

    private static final class StubSessionRepository extends SessionRepository {
        private UUID revokedUserId;

        private StubSessionRepository() {
            super(null);
        }

        @Override
        public void revokeAllForUser(UUID userId) {
            revokedUserId = userId;
        }
    }

    private static final class PrefixPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals(encode(rawPassword));
        }
    }
}
