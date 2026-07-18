package com.pixierge.api.filesystem;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FilesystemWatcherHealth {

    private volatile String status = "stopped";
    private volatile String lastErrorCode;
    private volatile String lastErrorMessage;
    private volatile OffsetDateTime lastErrorAt;
    private volatile OffsetDateTime lastOverflowAt;
    private volatile OffsetDateTime lastRegistrationRefreshAt;
    private volatile int registeredRootCount;
    private volatile int registeredDirectoryCount;

    public FilesystemWatcherHealthSnapshot snapshot() {
        return new FilesystemWatcherHealthSnapshot(
                status,
                lastErrorCode,
                lastErrorMessage,
                lastErrorAt,
                lastOverflowAt,
                lastRegistrationRefreshAt,
                registeredRootCount,
                registeredDirectoryCount
        );
    }

    void recordStarted() {
        status = "healthy";
    }

    void recordStopped() {
        status = "stopped";
    }

    void recordRegistrationRefresh(int rootCount, int directoryCount, boolean healthy) {
        registeredRootCount = rootCount;
        registeredDirectoryCount = directoryCount;
        lastRegistrationRefreshAt = OffsetDateTime.now();
        if (healthy) {
            status = "healthy";
        }
    }

    void recordOverflow(String message) {
        lastOverflowAt = OffsetDateTime.now();
        recordDegraded("watcher_overflow", message);
    }

    void recordDegraded(String errorCode, String message) {
        status = "degraded";
        lastErrorCode = errorCode;
        lastErrorMessage = message == null || message.isBlank() ? errorCode : message;
        lastErrorAt = OffsetDateTime.now();
    }
}
