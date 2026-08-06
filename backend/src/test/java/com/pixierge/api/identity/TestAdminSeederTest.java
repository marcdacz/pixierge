package com.pixierge.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class TestAdminSeederTest {

  @Test
  void runCreatesAdminWhenSeedUserIsMissing() {
    RecordingUserRepository userRepository = new RecordingUserRepository();
    TestAdminSeeder seeder =
        new TestAdminSeeder(new PrefixPasswordEncoder(), userRepository, "AdminDacz", "secret");

    seeder.run(new DefaultApplicationArguments());

    assertThat(userRepository.createdUsername).isEqualTo("AdminDacz");
    assertThat(userRepository.createdPasswordHash).isEqualTo("hash:secret");
    assertThat(userRepository.assignedRole).isEqualTo(IdentityConstants.ROLE_ADMIN);
  }

  @Test
  void runLeavesExistingAdminUntouched() {
    RecordingUserRepository userRepository = new RecordingUserRepository();
    userRepository.existingCredential =
        Optional.of(new UserRepository.LoginCredential(UUID.randomUUID(), "hash:old"));
    TestAdminSeeder seeder =
        new TestAdminSeeder(new PrefixPasswordEncoder(), userRepository, "AdminDacz", "secret");

    seeder.run(new DefaultApplicationArguments());

    assertThat(userRepository.createdUsername).isNull();
    assertThat(userRepository.assignedRole).isNull();
  }

  private static final class RecordingUserRepository extends UserRepository {

    private Optional<LoginCredential> existingCredential = Optional.empty();
    private String createdUsername;
    private String createdPasswordHash;
    private String assignedRole;

    private RecordingUserRepository() {
      super(null);
    }

    @Override
    public Optional<LoginCredential> findLoginCredential(String username) {
      return existingCredential;
    }

    @Override
    public UUID createUser(String username, String passwordHash) {
      createdUsername = username;
      createdPasswordHash = passwordHash;
      return UUID.randomUUID();
    }

    @Override
    public void assignRole(UUID userId, String roleKey) {
      assignedRole = roleKey;
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
