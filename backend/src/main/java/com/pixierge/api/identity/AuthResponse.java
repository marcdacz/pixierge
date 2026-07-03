package com.pixierge.api.identity;

public record AuthResponse(CurrentUserResponse user, String csrfToken) {

    public static AuthResponse from(AuthenticatedUser user) {
        return new AuthResponse(CurrentUserResponse.from(user), user.csrfToken());
    }
}
