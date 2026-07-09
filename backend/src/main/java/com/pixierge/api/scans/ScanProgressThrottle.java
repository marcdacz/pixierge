package com.pixierge.api.scans;

import java.util.UUID;

final class ScanProgressThrottle {

    static final int FILE_INTERVAL = 25;
    static final long TIME_INTERVAL_MS = 2_000L;

    private final UUID scanRunId;
    private final ScanRepository scanRepository;
    private final ScanCounts counts;
    private int lastPublishedCount;
    private long lastPublishedAtMillis;

    ScanProgressThrottle(UUID scanRunId, ScanRepository scanRepository, ScanCounts counts) {
        this.scanRunId = scanRunId;
        this.scanRepository = scanRepository;
        this.counts = counts;
        this.lastPublishedAtMillis = System.currentTimeMillis();
    }

    void maybeUpdate() {
        if (shouldUpdate()) {
            flush();
        }
    }

    void flush() {
        scanRepository.updateScanRunProgress(scanRunId, counts);
        lastPublishedCount = counts.scannedFileCount();
        lastPublishedAtMillis = System.currentTimeMillis();
    }

    boolean shouldUpdate() {
        return counts.scannedFileCount() - lastPublishedCount >= FILE_INTERVAL
                || System.currentTimeMillis() - lastPublishedAtMillis >= TIME_INTERVAL_MS;
    }
}
