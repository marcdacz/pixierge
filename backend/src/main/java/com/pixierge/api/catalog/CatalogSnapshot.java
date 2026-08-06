package com.pixierge.api.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

record CatalogSnapshot(
    UUID id,
    OffsetDateTime createdAt,
    long throughSequence,
    String storagePath,
    String checksum,
    long byteSize,
    String status,
    String failureDetail) {}
