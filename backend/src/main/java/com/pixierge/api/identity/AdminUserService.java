package com.pixierge.api.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AdminUserService {

  private final UserRepository userRepository;
  private final SessionRepository sessionRepository;
  private final PasswordEncoder passwordEncoder;

  AdminUserService(
      UserRepository userRepository,
      SessionRepository sessionRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  UserSummaryResponse create(String username, String password) {
    AccountInput input = AuthService.validateAccountInput(username, password);
    if (userRepository.usernameExists(input.username())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }
    UUID userId =
        userRepository.createUser(input.username(), passwordEncoder.encode(input.password()));
    userRepository.assignRole(userId, IdentityConstants.ROLE_USER);
    return userRepository.requireUserSummary(userId);
  }

  @Transactional
  void resetPassword(UUID userId, String password) {
    requireUser(userId);
    AccountInput input = AuthService.validateAccountInput("valid-user", password);
    userRepository.updatePassword(userId, passwordEncoder.encode(input.password()));
    sessionRepository.revokeAllForUser(userId);
  }

  @Transactional
  UserSummaryResponse updateStatus(UUID userId, boolean active) {
    UserRepository.UserRecord user = requireUser(userId);
    if (!active
        && IdentityConstants.USER_STATUS_ACTIVE.equals(user.status())
        && userRepository.isLastActiveAdmin(userId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "At least one active administrator is required");
    }
    userRepository.updateStatus(
        userId,
        active ? IdentityConstants.USER_STATUS_ACTIVE : IdentityConstants.USER_STATUS_DISABLED);
    if (!active) {
      sessionRepository.revokeAllForUser(userId);
    }
    return userRepository.requireUserSummary(userId);
  }

  @Transactional
  void delete(UUID actorId, UUID userId, UUID replacementUserId) {
    if (actorId.equals(userId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "You cannot delete your own account");
    }
    if (replacementUserId == null || userId.equals(replacementUserId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Replacement user must be different from the deleted account");
    }
    UserRepository.UserRecord user = requireUser(userId);
    UserRepository.UserRecord replacement = requireUser(replacementUserId);
    if (!IdentityConstants.USER_STATUS_ACTIVE.equals(replacement.status())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replacement user must be active");
    }
    if (user.roles().contains(IdentityConstants.ROLE_ADMIN)
        && userRepository.isLastActiveAdmin(userId)
        && !replacement.roles().contains(IdentityConstants.ROLE_ADMIN)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Replacement user must be an administrator");
    }
    userRepository.transferOwnership(userId, replacementUserId);
    sessionRepository.revokeAllForUser(userId);
    userRepository.deleteUser(userId);
  }

  private UserRepository.UserRecord requireUser(UUID userId) {
    return userRepository
        .findUser(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}
