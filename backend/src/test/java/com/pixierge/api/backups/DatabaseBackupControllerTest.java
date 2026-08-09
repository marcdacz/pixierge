package com.pixierge.api.backups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseBackupControllerTest {
  @TempDir Path tempDir;

  @Test
  void exposesBackupHistoryAndDownloadsAsAttachments() throws Exception {
    DatabaseBackupService service = mock(DatabaseBackupService.class);
    UUID id = UUID.randomUUID();
    Path backupFile = tempDir.resolve("backup.dump");
    Files.writeString(backupFile, "dump");
    when(service.history(1, 50))
        .thenReturn(new DatabaseBackupService.DatabaseBackupHistory(List.of(), 1, 50, 0, false));
    when(service.download(id))
        .thenReturn(new DatabaseBackupService.DatabaseBackupDownload("backup.dump", backupFile, 4));
    DatabaseBackupController controller = new DatabaseBackupController(service);

    assertThat(controller.history(1, 50).page()).isEqualTo(1);
    var download = controller.download(id);

    assertThat(download.getHeaders().getContentDisposition().getFilename())
        .isEqualTo("backup.dump");
    assertThat(download.getHeaders().getContentLength()).isEqualTo(4);
    assertThat(download.getBody().getInputStream().readAllBytes()).isEqualTo("dump".getBytes());
  }

  @Test
  void forwardsRestorePath() {
    DatabaseBackupService service = mock(DatabaseBackupService.class);
    new DatabaseBackupController(service)
        .restore(new DatabaseBackupController.RestoreDatabaseBackupRequest("weekly.dump"), null);
    verify(service).restore("weekly.dump");
  }
}
