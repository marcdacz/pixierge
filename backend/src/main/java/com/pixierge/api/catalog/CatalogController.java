package com.pixierge.api.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController {
  private final CatalogService catalogService;

  CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/api/admin/catalog/status")
  CatalogStatusResponse status() {
    return catalogService.status();
  }

  @GetMapping("/api/admin/catalog/history")
  CatalogHistoryResponse history(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int pageSize) {
    return catalogService.history(page, pageSize);
  }

  @GetMapping("/api/admin/catalog/history/{snapshotId}/download")
  ResponseEntity<InputStreamResource> download(@PathVariable UUID snapshotId) throws IOException {
    CatalogService.CatalogExportDownload export = catalogService.download(snapshotId);
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf("application/x-ndjson"))
        .contentLength(export.byteSize())
        .header(
            "Content-Disposition",
            ContentDisposition.attachment().filename(export.fileName()).build().toString())
        .body(new InputStreamResource(Files.newInputStream(export.path())));
  }

  @PostMapping("/api/admin/catalog/export")
  @ResponseStatus(HttpStatus.ACCEPTED)
  CatalogSnapshotResponse exportNow() {
    return catalogService.exportNow();
  }
}
