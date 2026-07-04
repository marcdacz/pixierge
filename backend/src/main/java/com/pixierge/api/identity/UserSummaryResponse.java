package com.pixierge.api.identity;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String status,
        Set<String> roles,
        OffsetDateTime createdAt
) {
}
