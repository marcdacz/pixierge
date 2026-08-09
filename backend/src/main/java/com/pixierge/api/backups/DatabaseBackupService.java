package com.pixierge.api.backups;

import com.pixierge.api.assets.StorageProperties;
import com.pixierge.api.catalog.CatalogService;
import com.pixierge.api.catalog.SystemCatalogChanges;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatabaseBackupService {
  private static final int PAGE_SIZE_MAX = 100;
  private static final long MAX_IMPORT_BYTES = 20L * 1024 * 1024 * 1024;
  private final DatabaseBackupRepository repository;
  private final JdbcTemplate jdbcTemplate;
  private final Flyway flyway;
  private final CatalogService auditService;
  private final Path storageRoot;
  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final ReentrantLock operationLock = new ReentrantLock();

  @Autowired
  public DatabaseBackupService(
      DatabaseBackupRepository repository,
      DataSource dataSource,
      Flyway flyway,
      CatalogService auditService,
      StorageProperties storageProperties,
      @Value("${spring.datasource.url}") String jdbcUrl,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password) {
    this(
        repository,
        new JdbcTemplate(dataSource),
        flyway,
        auditService,
        storageProperties,
        jdbcUrl,
        username,
        password);
  }

  DatabaseBackupService(
      DatabaseBackupRepository repository,
      JdbcTemplate jdbcTemplate,
      Flyway flyway,
      CatalogService auditService,
      StorageProperties storageProperties,
      String jdbcUrl,
      String username,
      String password) {
    this.repository = repository;
    this.jdbcTemplate = jdbcTemplate;
    this.flyway = flyway;
    this.auditService = auditService;
    this.storageRoot = Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
  }

  @Transactional(readOnly = true)
  public DatabaseBackupHistory history(int page, int pageSize) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(PAGE_SIZE_MAX, Math.max(1, pageSize));
    List<DatabaseBackup> rows = repository.history(safePage * safeSize, safeSize + 1);
    return new DatabaseBackupHistory(
        rows.stream().limit(safeSize).map(DatabaseBackupResponse::from).toList(),
        safePage,
        safeSize,
        repository.count(),
        rows.size() > safeSize);
  }

  public DatabaseBackupResponse create() {
    operationLock.lock();
    try {
      return DatabaseBackupResponse.from(createInternal());
    } finally {
      operationLock.unlock();
    }
  }

  public DatabaseBackupDownload download(UUID id) {
    DatabaseBackup backup =
        repository
            .find(id)
            .filter(item -> "completed".equals(item.status()))
            .orElseThrow(() -> new DatabaseBackupNotFoundException(id));
    try {
      Path path = resolveStored(Path.of(backup.storagePath()));
      long byteSize = Files.size(path);
      if (byteSize != backup.byteSize() || !checksum(path).equals(backup.checksum()))
        throw new IllegalStateException("Database backup integrity check failed");
      return new DatabaseBackupDownload(
          "pixierge-database-backup-" + backup.id() + ".dump", path, byteSize);
    } catch (IOException exception) {
      throw new IllegalStateException("Database backup could not be read", exception);
    }
  }

  public void restore(String relativePath) {
    operationLock.lock();
    try {
      Path importFile = resolveImport(relativePath);
      validateArchive(importFile);
      DatabaseBackup restorePoint = createInternal();
      if (!"completed".equals(restorePoint.status())) {
        throw new IllegalStateException(
            "Database restore was not started because the pre-restore backup failed: "
                + restorePoint.failureDetail());
      }
      try {
        run(
            command(
                "pg_restore",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "--exit-on-error",
                importFile.toString()));
        flyway.migrate();
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      } catch (RuntimeException failure) {
        run(
            command(
                "pg_restore",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "--exit-on-error",
                resolveStored(Path.of(restorePoint.storagePath())).toString()));
        throw new IllegalStateException(
            "Database restore failed and the pre-restore backup was reapplied", failure);
      }
    } finally {
      operationLock.unlock();
    }
  }

  private DatabaseBackup createInternal() {
    UUID id = UUID.randomUUID();
    String timestamp =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(OffsetDateTime.now(ZoneOffset.UTC));
    Path relative = Path.of("backups", "pixierge-db-" + timestamp + "-" + id + ".dump");
    Path target = resolveStored(relative);
    Path temporary = target.resolveSibling("." + target.getFileName() + ".tmp");
    try {
      Files.createDirectories(target.getParent());
      run(
          command(
              "pg_dump",
              "--format=custom",
              "--no-owner",
              "--no-privileges",
              "--file=" + temporary));
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      long byteSize = Files.size(target);
      DatabaseBackup backup =
          new DatabaseBackup(
              id,
              OffsetDateTime.now(),
              relative.toString(),
              checksum(target),
              byteSize,
              jdbcTemplate.queryForObject("SHOW server_version", String.class),
              schemaVersion(),
              "completed",
              null);
      repository.add(backup);
      auditService.record(
          SystemCatalogChanges.changed(
              id, "database_backup_created", relative.getFileName().toString()),
          null);
      return backup;
    } catch (IOException | RuntimeException exception) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
      DatabaseBackup failed =
          new DatabaseBackup(
              id,
              OffsetDateTime.now(),
              relative.toString(),
              "",
              0,
              postgresVersion(),
              schemaVersion(),
              "failed",
              sanitize(exception.getMessage()));
      repository.add(failed);
      return failed;
    }
  }

  private void validateArchive(Path file) {
    try {
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
          || Files.size(file) > MAX_IMPORT_BYTES)
        throw badRestoreRequest(
            "Database backup must be a regular .dump file below the import size limit");
      run(command("pg_restore", "--list", file.toString()));
    } catch (IOException exception) {
      throw badRestoreRequest("Database backup could not be validated", exception);
    } catch (IllegalStateException exception) {
      throw badRestoreRequest("Database backup could not be validated", exception);
    }
  }

  private List<String> command(String executable, String... extra) {
    DatabaseTarget target = DatabaseTarget.parse(jdbcUrl);
    List<String> command =
        new ArrayList<>(
            List.of(
                executable,
                "--host=" + target.host(),
                "--port=" + target.port(),
                "--username=" + username,
                "--dbname=" + target.database()));
    command.addAll(List.of(extra));
    return command;
  }

  protected void run(List<String> command) {
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().put("PGPASSWORD", password);
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes());
      if (process.waitFor() != 0)
        throw new IllegalStateException("PostgreSQL backup command failed: " + sanitize(output));
    } catch (IOException exception) {
      throw new IllegalStateException("PostgreSQL client tools are unavailable", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PostgreSQL backup command was interrupted", exception);
    }
  }

  private Path resolveStored(Path relative) {
    return resolveWithin(storageRoot, relative);
  }

  private Path resolveImport(String value) {
    if (value == null || value.isBlank())
      throw badRestoreRequest("Database backup path is required");
    try {
      return resolveWithin(storageRoot.resolve("recovery-import"), Path.of(value));
    } catch (IllegalArgumentException exception) {
      throw badRestoreRequest("Database backup path escapes its allowed directory", exception);
    }
  }

  private Path resolveWithin(Path base, Path relative) {
    Path normalizedBase = base.toAbsolutePath().normalize();
    Path resolved = normalizedBase.resolve(relative).normalize();
    if (relative.isAbsolute() || !resolved.startsWith(normalizedBase))
      throw new IllegalArgumentException("Database backup path escapes its allowed directory");
    return resolved;
  }

  private String postgresVersion() {
    try {
      return jdbcTemplate.queryForObject("SHOW server_version", String.class);
    } catch (RuntimeException ignored) {
      return "unknown";
    }
  }

  private String schemaVersion() {
    return flyway.info().current() == null
        ? "0"
        : flyway.info().current().getVersion().getVersion();
  }

  private String checksum(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String sanitize(String value) {
    return value == null
        ? "Database backup failed"
        : value.replaceAll("[\\r\\n]", " ").substring(0, Math.min(value.length(), 500));
  }

  private ResponseStatusException badRestoreRequest(String reason) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }

  private ResponseStatusException badRestoreRequest(String reason, Throwable cause) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason, cause);
  }

  record DatabaseTarget(String host, String port, String database) {
    static DatabaseTarget parse(String value) {
      java.net.URI uri = java.net.URI.create(value.substring("jdbc:".length()));
      return new DatabaseTarget(
          uri.getHost(),
          uri.getPort() < 0 ? "5432" : Integer.toString(uri.getPort()),
          uri.getPath().substring(1));
    }
  }

  public record DatabaseBackupResponse(
      UUID id,
      OffsetDateTime createdAt,
      long byteSize,
      String checksum,
      String postgresVersion,
      String schemaVersion,
      String status,
      String failureDetail) {
    static DatabaseBackupResponse from(DatabaseBackup value) {
      return new DatabaseBackupResponse(
          value.id(),
          value.createdAt(),
          value.byteSize(),
          value.checksum(),
          value.postgresVersion(),
          value.schemaVersion(),
          value.status(),
          value.failureDetail());
    }
  }

  public record DatabaseBackupHistory(
      List<DatabaseBackupResponse> items,
      int page,
      int pageSize,
      long totalCount,
      boolean hasNext) {}

  public record DatabaseBackupDownload(String fileName, Path path, long byteSize) {}

  static final class DatabaseBackupNotFoundException extends RuntimeException {
    DatabaseBackupNotFoundException(UUID id) {
      super("Database backup not found: " + id);
    }
  }
}
