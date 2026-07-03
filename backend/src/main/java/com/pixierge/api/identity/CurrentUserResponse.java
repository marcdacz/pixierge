package com.pixierge.api.identity;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName,
        Set<String> roles,
        Set<String> permissions
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.email(), user.displayName(), user.roles(), user.permissions());
    }
}
