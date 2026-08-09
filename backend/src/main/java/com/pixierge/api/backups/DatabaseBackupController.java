package com.pixierge.api.backups;

import com.pixierge.api.identity.AuthenticatedUser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DatabaseBackupController {
  private final DatabaseBackupService backupService;

  DatabaseBackupController(DatabaseBackupService backupService) {
    this.backupService = backupService;
  }

  @GetMapping("/api/admin/backups")
  DatabaseBackupService.DatabaseBackupHistory history(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int pageSize) {
    return backupService.history(page, pageSize);
  }

  @PostMapping("/api/admin/backups")
  DatabaseBackupService.DatabaseBackupResponse create(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return backupService.create();
  }

  @GetMapping("/api/admin/backups/{backupId}/download")
  ResponseEntity<InputStreamResource> download(@PathVariable UUID backupId) throws IOException {
    DatabaseBackupService.DatabaseBackupDownload backup = backupService.download(backupId);
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf("application/octet-stream"))
        .contentLength(backup.byteSize())
        .header(
            "Content-Disposition",
            ContentDisposition.attachment().filename(backup.fileName()).build().toString())
        .body(new InputStreamResource(Files.newInputStream(backup.path())));
  }

  @PostMapping("/api/admin/backups/restore")
  void restore(
      @RequestBody RestoreDatabaseBackupRequest request,
      @AuthenticationPrincipal AuthenticatedUser user) {
    backupService.restore(request.path());
  }

  record RestoreDatabaseBackupRequest(String path) {}
}
