package com.pixierge.api.filesystem;

import java.time.OffsetDateTime;

public record FilesystemWatcherHealthSnapshot(
        String status,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime lastErrorAt,
        OffsetDateTime lastOverflowAt,
        OffsetDateTime lastRegistrationRefreshAt,
        int registeredRootCount,
        int registeredDirectoryCount
) {
}
