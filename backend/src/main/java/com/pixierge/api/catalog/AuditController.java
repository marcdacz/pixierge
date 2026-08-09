package com.pixierge.api.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditController {
  private final CatalogService catalogService;

  AuditController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/api/admin/audit/events")
  CatalogService.AuditHistoryResponse events(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int pageSize,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID actor,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return catalogService.auditHistory(page, pageSize, q, actor, from, to);
  }
}
