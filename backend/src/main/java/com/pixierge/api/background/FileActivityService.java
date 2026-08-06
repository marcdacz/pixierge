package com.pixierge.api.background;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileActivityService {
  private final BackgroundActivityRepository repository;
  private final int retentionDays;

  FileActivityService(
      BackgroundActivityRepository repository,
      @Value("${pixierge.file-activity.retention-days:90}") int retentionDays) {
    this.repository = repository;
    this.retentionDays = Math.max(1, retentionDays);
  }

  public static FileActivityService noop() {
    return new FileActivityService(null, 90);
  }

  public void record(
      UUID assetId,
      String path,
      String status,
      OffsetDateTime occurredAt,
      String message,
      Long durationMs,
      UUID jobId,
      String batchLabel) {
    if (repository != null)
      repository.create(assetId, path, status, occurredAt, message, durationMs, jobId, batchLabel);
  }

  public long deleteExpired() {
    return repository == null
        ? 0L
        : repository.deleteOlderThan(OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays));
  }
}
