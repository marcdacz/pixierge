package com.pixierge.api.identity;

import com.pixierge.api.catalog.CatalogChange;
import com.pixierge.api.catalog.CatalogService;
import com.pixierge.api.catalog.UserCatalogChanges;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final CatalogService catalogService;

  @Autowired
  AdminUserService(
      UserRepository userRepository,
      SessionRepository sessionRepository,
      PasswordEncoder passwordEncoder,
      CatalogService catalogService) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.passwordEncoder = passwordEncoder;
    this.catalogService = catalogService;
  }

  AdminUserService(
      UserRepository userRepository,
      SessionRepository sessionRepository,
      PasswordEncoder passwordEncoder) {
    this(userRepository, sessionRepository, passwordEncoder, null);
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
    UserSummaryResponse created = userRepository.requireUserSummary(userId);
    record(UserCatalogChanges.created(userId, created.username(), created.roles()), null);
    return created;
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
    UserSummaryResponse updated = userRepository.requireUserSummary(userId);
    record(UserCatalogChanges.statusChanged(userId, updated.status()), null);
    return updated;
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
    record(UserCatalogChanges.ownershipTransferred(userId, replacementUserId), actorId);
  }

  private UserRepository.UserRecord requireUser(UUID userId) {
    return userRepository
        .findUser(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  private void record(CatalogChange change, UUID actorId) {
    if (catalogService != null) {
      catalogService.record(change, actorId);
    }
  }
}
