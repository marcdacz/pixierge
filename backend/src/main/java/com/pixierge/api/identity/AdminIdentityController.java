package com.pixierge.api.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminIdentityController {

  private final UserRepository userRepository;
  private final AdminUserService adminUserService;

  public AdminIdentityController(UserRepository userRepository, AdminUserService adminUserService) {
    this.userRepository = userRepository;
    this.adminUserService = adminUserService;
  }

  @GetMapping("/users")
  List<UserSummaryResponse> users() {
    return userRepository.listUsers();
  }

  @GetMapping("/roles")
  List<RoleSummaryResponse> roles() {
    return userRepository.listRoles();
  }

  @PostMapping("/users")
  UserSummaryResponse createUser(@RequestBody CreateUserRequest request) {
    return adminUserService.create(request.username(), request.password());
  }

  @PostMapping("/users/{userId}/reset-password")
  void resetPassword(@PathVariable UUID userId, @RequestBody ResetPasswordRequest request) {
    adminUserService.resetPassword(userId, request.password());
  }

  @PatchMapping("/users/{userId}")
  UserSummaryResponse updateUser(
      @PathVariable UUID userId, @RequestBody UpdateUserRequest request) {
    return adminUserService.updateStatus(userId, request.active());
  }

  @DeleteMapping("/users/{userId}")
  void deleteUser(
      @PathVariable UUID userId,
      @RequestBody DeleteUserRequest request,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    adminUserService.delete(actor.id(), userId, request.replacementUserId());
  }

  record CreateUserRequest(String username, String password) {}

  record ResetPasswordRequest(String password) {}

  record UpdateUserRequest(boolean active) {}

  record DeleteUserRequest(UUID replacementUserId) {}
}
