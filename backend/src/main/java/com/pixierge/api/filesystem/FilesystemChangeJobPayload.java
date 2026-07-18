package com.pixierge.api.filesystem;

import java.util.UUID;

public record FilesystemChangeJobPayload(
        UUID libraryId,
        UUID rootId,
        String path,
        String eventType
) {
}
