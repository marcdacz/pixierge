package com.pixierge.api.backups;

import java.time.OffsetDateTime;
import java.util.UUID;

record DatabaseBackup(
    UUID id,
    OffsetDateTime createdAt,
    String storagePath,
    String checksum,
    long byteSize,
    String postgresVersion,
    String schemaVersion,
    String status,
    String failureDetail) {}
