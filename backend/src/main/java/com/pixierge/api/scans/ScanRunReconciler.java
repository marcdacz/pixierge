package com.pixierge.api.scans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ScanRunReconciler {

  private static final Logger log = LoggerFactory.getLogger(ScanRunReconciler.class);

  private final ScanService scanService;

  ScanRunReconciler(ScanService scanService) {
    this.scanService = scanService;
  }

  @EventListener(ApplicationReadyEvent.class)
  void reconcileOrphanedQueuedScans() {
    int reconciled = scanService.reconcileOrphanedQueuedScans();
    if (reconciled > 0) {
      log.warn("Marked {} orphaned queued scan run(s) as failed", reconciled);
    }
  }
}
