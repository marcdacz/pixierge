package com.pixierge.api.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

  @PostMapping("/api/admin/catalog/export")
  @ResponseStatus(HttpStatus.ACCEPTED)
  CatalogSnapshotResponse exportNow() {
    return catalogService.exportNow();
  }
}
