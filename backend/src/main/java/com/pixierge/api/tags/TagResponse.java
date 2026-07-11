package com.pixierge.api.tags;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TagResponse(UUID id, String name, int assetCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
