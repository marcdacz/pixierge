package com.pixierge.api.identity;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        String displayName,
        Set<String> roles,
        Set<String> permissions,
        String csrfToken
) {
}
