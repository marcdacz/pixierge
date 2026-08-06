package com.pixierge.api.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record CatalogStatusResponse(
    String status,
    long latestSequence,
    long exportedThroughSequence,
    long pendingEventCount,
    String failureDetail) {}

record CatalogSnapshotResponse(
    UUID id,
    OffsetDateTime createdAt,
    long throughSequence,
    long byteSize,
    String checksum,
    String status,
    String failureDetail) {}

record CatalogHistoryResponse(
    List<CatalogSnapshotResponse> items, int page, int pageSize, boolean hasNext) {}
