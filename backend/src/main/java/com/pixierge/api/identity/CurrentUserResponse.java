package com.pixierge.api.identity;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String username,
        Set<String> roles,
        Set<String> permissions
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.username(), user.roles(), user.permissions());
    }
}
