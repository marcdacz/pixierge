package com.pixierge.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

class CatalogControllerTest {
  @TempDir Path tempDir;

  @Test
  void downloadsCompletedCatalogExportsAsAttachments() throws Exception {
    CatalogService service = mock(CatalogService.class);
    UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    byte[] bytes = "catalog export".getBytes();
    Path exportFile = tempDir.resolve("catalog-export.ndjson");
    Files.write(exportFile, bytes);
    when(service.download(snapshotId))
        .thenReturn(
            new CatalogService.CatalogExportDownload(
                "catalog-export.ndjson", exportFile, bytes.length));

    var response = new CatalogController(service).download(snapshotId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("application/x-ndjson"));
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo("catalog-export.ndjson");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
    assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(bytes);
  }
}
