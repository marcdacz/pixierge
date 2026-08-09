package com.pixierge.api.backups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixierge.api.assets.StorageProperties;
import com.pixierge.api.catalog.CatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class DatabaseBackupServiceTest {
  @TempDir Path root;

  @Test
  void createsAChecksummedCustomBackupAndAuditsIt() {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    CatalogService audit = mock(CatalogService.class);
    DatabaseBackupService service = service(repository, audit);

    DatabaseBackupService.DatabaseBackupResponse backup = service.create();

    assertThat(backup.status()).isEqualTo("completed");
    assertThat(backup.byteSize()).isGreaterThan(0);
    assertThat(backup.checksum()).hasSize(64);
    verify(repository).add(any(DatabaseBackup.class));
    verify(audit).record(any(), any());
  }

  @Test
  void rejectsTraversalBeforeRunningRestore() {
    DatabaseBackupService service =
        service(mock(DatabaseBackupRepository.class), mock(CatalogService.class));
    assertThatThrownBy(() -> service.restore("../outside.dump"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode", "reason")
        .containsExactly(
            HttpStatus.BAD_REQUEST, "Database backup path escapes its allowed directory");
  }

  @Test
  void rejectsMissingRestoreArchiveAsBadRequest() {
    DatabaseBackupService service =
        service(mock(DatabaseBackupRepository.class), mock(CatalogService.class));
    assertThatThrownBy(() -> service.restore("missing.dump"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode", "reason")
        .containsExactly(
            HttpStatus.BAD_REQUEST,
            "Database backup must be a regular .dump file below the import size limit");
  }

  @Test
  void rejectsUnvalidatedRestoreArchiveAsBadRequest() throws Exception {
    Path staged = root.resolve("recovery-import/broken.dump");
    Files.createDirectories(staged.getParent());
    Files.writeString(staged, "broken");
    DatabaseBackupService service =
        service(
            mock(DatabaseBackupRepository.class),
            mock(CatalogService.class),
            new ArrayList<>(),
            false,
            true);
    assertThatThrownBy(() -> service.restore("broken.dump"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode", "reason")
        .containsExactly(HttpStatus.BAD_REQUEST, "Database backup could not be validated");
  }

  @Test
  void downloadVerifiesChecksumBeforeReturningBytes() throws Exception {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    Path file = root.resolve("backups/one.dump");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "dump");
    when(repository.find(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new DatabaseBackup(
                        invocation.getArgument(0),
                        OffsetDateTime.now(),
                        "backups/one.dump",
                        "0".repeat(64),
                        4,
                        "16",
                        "22",
                        "completed",
                        null)));

    assertThatThrownBy(
            () -> service(repository, mock(CatalogService.class)).download(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("integrity");
  }

  @Test
  void parsesJdbcPostgresTargets() {
    assertThat(
            DatabaseBackupService.DatabaseTarget.parse(
                "jdbc:postgresql://db.example:5544/pixierge"))
        .isEqualTo(new DatabaseBackupService.DatabaseTarget("db.example", "5544", "pixierge"));
  }

  @Test
  void clampsBackupHistoryAndReportsTheNextPage() {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    DatabaseBackup row =
        new DatabaseBackup(
            UUID.randomUUID(),
            OffsetDateTime.now(),
            "backups/a.dump",
            "a",
            1,
            "16",
            "22",
            "completed",
            null);
    when(repository.history(0, 2)).thenReturn(List.of(row, row));
    when(repository.count()).thenReturn(4L);

    DatabaseBackupService.DatabaseBackupHistory history =
        service(repository, mock(CatalogService.class)).history(-1, 1);

    assertThat(history)
        .extracting(
            DatabaseBackupService.DatabaseBackupHistory::page,
            DatabaseBackupService.DatabaseBackupHistory::pageSize,
            DatabaseBackupService.DatabaseBackupHistory::totalCount,
            DatabaseBackupService.DatabaseBackupHistory::hasNext)
        .containsExactly(0, 1, 4L, true);
    assertThat(history.items()).hasSize(1);
  }

  @Test
  void downloadsAValidBackup() throws Exception {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    Path file = root.resolve("backups/valid.dump");
    Files.createDirectories(file.getParent());
    byte[] bytes = "valid dump".getBytes();
    Files.write(file, bytes);
    DatabaseBackup backup =
        new DatabaseBackup(
            UUID.randomUUID(),
            OffsetDateTime.now(),
            "backups/valid.dump",
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
            bytes.length,
            "16",
            "22",
            "completed",
            null);
    when(repository.find(backup.id())).thenReturn(Optional.of(backup));

    DatabaseBackupService.DatabaseBackupDownload download =
        service(repository, mock(CatalogService.class)).download(backup.id());

    assertThat(download.fileName()).endsWith(".dump");
    assertThat(download.byteSize()).isEqualTo(bytes.length);
    assertThat(Files.readAllBytes(download.path())).isEqualTo(bytes);
  }

  @Test
  void restoresAValidatedArchiveAfterCreatingARestorePoint() throws Exception {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    Path staged = root.resolve("recovery-import/restore.dump");
    Files.createDirectories(staged.getParent());
    Files.writeString(staged, "restore");
    List<List<String>> commands = new ArrayList<>();
    DatabaseBackupService service =
        service(repository, mock(CatalogService.class), commands, false);

    service.restore("restore.dump");

    assertThat(commands)
        .anyMatch(command -> command.getFirst().equals("pg_restore") && command.contains("--list"));
    assertThat(commands).anyMatch(command -> command.getFirst().equals("pg_dump"));
    assertThat(commands)
        .anyMatch(
            command -> command.getFirst().equals("pg_restore") && command.contains("--clean"));
  }

  @Test
  void restoresTheRestorePointWhenImportFails() throws Exception {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    Path staged = root.resolve("recovery-import/broken.dump");
    Files.createDirectories(staged.getParent());
    Files.writeString(staged, "broken");
    List<DatabaseBackup> saved = new ArrayList<>();
    doAnswer(
            invocation -> {
              saved.add(invocation.getArgument(0));
              return null;
            })
        .when(repository)
        .add(any(DatabaseBackup.class));
    List<List<String>> commands = new ArrayList<>();
    DatabaseBackupService service = service(repository, mock(CatalogService.class), commands, true);

    assertThatThrownBy(() -> service.restore("broken.dump"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pre-restore backup was reapplied");
    assertThat(
            commands.stream()
                .filter(
                    command ->
                        command.getFirst().equals("pg_restore") && command.contains("--clean")))
        .hasSize(2);
  }

  @Test
  void doesNotStartRestoreWhenRestorePointCannotBeCreated() throws Exception {
    DatabaseBackupRepository repository = mock(DatabaseBackupRepository.class);
    Path staged = root.resolve("recovery-import/restore.dump");
    Files.createDirectories(staged.getParent());
    Files.writeString(staged, "restore");
    List<List<String>> commands = new ArrayList<>();
    DatabaseBackupService service =
        service(repository, mock(CatalogService.class), commands, false, false, true);

    assertThatThrownBy(() -> service.restore("restore.dump"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pre-restore backup failed");
    assertThat(commands.stream().filter(command -> command.getFirst().equals("pg_restore")))
        .hasSize(1);
  }

  private DatabaseBackupService service(DatabaseBackupRepository repository, CatalogService audit) {
    return service(repository, audit, new ArrayList<>(), false);
  }

  private DatabaseBackupService service(
      DatabaseBackupRepository repository,
      CatalogService audit,
      List<List<String>> commands,
      boolean failImport) {
    return service(repository, audit, commands, failImport, false);
  }

  private DatabaseBackupService service(
      DatabaseBackupRepository repository,
      CatalogService audit,
      List<List<String>> commands,
      boolean failImport,
      boolean failValidation) {
    return service(repository, audit, commands, failImport, failValidation, false);
  }

  private DatabaseBackupService service(
      DatabaseBackupRepository repository,
      CatalogService audit,
      List<List<String>> commands,
      boolean failImport,
      boolean failValidation,
      boolean failDump) {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("SHOW server_version", String.class)).thenReturn("16.4");
    Flyway flyway = mock(Flyway.class);
    MigrationInfoService info = mock(MigrationInfoService.class);
    when(flyway.info()).thenReturn(info);
    when(info.current()).thenReturn(null);
    StorageProperties storage = new StorageProperties();
    storage.setRoot(root.toString());
    return new DatabaseBackupService(
        repository,
        jdbc,
        flyway,
        audit,
        storage,
        "jdbc:postgresql://postgres:5432/pixierge",
        "pixierge",
        "secret") {
      @Override
      protected void run(List<String> command) {
        try {
          commands.add(List.copyOf(command));
          if (failValidation
              && command.getFirst().equals("pg_restore")
              && command.contains("--list")) {
            throw new IllegalStateException("archive rejected");
          }
          if (failDump && command.getFirst().equals("pg_dump")) {
            throw new IllegalStateException("backup rejected");
          }
          if (failImport
              && command.getFirst().equals("pg_restore")
              && command.contains("--clean")
              && command.getLast().endsWith("broken.dump")) {
            throw new IllegalStateException("restore rejected");
          }
          for (String argument : command)
            if (argument.startsWith("--file="))
              Files.writeString(Path.of(argument.substring(7)), "custom-dump");
        } catch (Exception exception) {
          throw new IllegalStateException(exception);
        }
      }
    };
  }
}
