package com.pixierge.api.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

record CatalogEvent(
    long sequence,
    UUID eventId,
    int eventVersion,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    UUID actorUserId,
    String payloadJson,
    OffsetDateTime createdAt) {}
