package com.pixierge.api.identity;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        Set<String> roles,
        Set<String> permissions,
        String csrfToken
) {
}
