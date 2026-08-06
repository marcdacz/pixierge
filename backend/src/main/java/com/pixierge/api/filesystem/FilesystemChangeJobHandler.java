package com.pixierge.api.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobDeferredException;
import com.pixierge.api.background.BackgroundJobHandler;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.scans.ScanJobTypes;
import com.pixierge.api.scans.ScanService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class FilesystemChangeJobHandler implements BackgroundJobHandler {

  private static final Duration ACTIVE_SCAN_RETRY_DELAY = Duration.ofSeconds(5);

  private final FilesystemChangeScanEnqueuer scanEnqueuer;
  private final ObjectMapper objectMapper;

  @Autowired
  FilesystemChangeJobHandler(ScanService scanService, ObjectMapper objectMapper) {
    this(scanService::enqueueFilesystemChangeScan, objectMapper);
  }

  FilesystemChangeJobHandler(FilesystemChangeScanEnqueuer scanEnqueuer, ObjectMapper objectMapper) {
    this.scanEnqueuer = scanEnqueuer;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobType() {
    return ScanJobTypes.FILESYSTEM_CHANGE_EVENT;
  }

  @Override
  public void handle(BackgroundJobRecord job) throws JsonProcessingException {
    FilesystemChangeJobPayload payload =
        objectMapper.readValue(job.payloadJson(), FilesystemChangeJobPayload.class);
    try {
      scanEnqueuer.enqueueFilesystemChangeScan(
          payload.libraryId(), payload.rootId(), payload.path());
    } catch (ScanService.ScanAlreadyActiveException exception) {
      throw new BackgroundJobDeferredException(exception.getMessage(), ACTIVE_SCAN_RETRY_DELAY);
    }
  }

  @FunctionalInterface
  interface FilesystemChangeScanEnqueuer {
    void enqueueFilesystemChangeScan(UUID libraryId, UUID rootId, String path);
  }
}
