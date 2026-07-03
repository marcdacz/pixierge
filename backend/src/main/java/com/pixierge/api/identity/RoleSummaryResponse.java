package com.pixierge.api.identity;

import java.util.Set;

public record RoleSummaryResponse(
        String key,
        String name,
        String description,
        Set<String> permissions
) {
}
