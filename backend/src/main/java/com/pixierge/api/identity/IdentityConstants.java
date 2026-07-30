package com.pixierge.api.identity;

final class IdentityConstants {

    static final String CSRF_HEADER = "X-Pixierge-Csrf";
    static final String PERMISSION_IDENTITY_ADMIN = "identity:admin";
    static final String ROLE_ADMIN = "ADMIN";
    static final String ROLE_USER = "USER";
    static final String SESSION_COOKIE_NAME = "pixierge_session";
    static final String USER_STATUS_ACTIVE = "active";
    static final String USER_STATUS_DISABLED = "disabled";

    private IdentityConstants() {
    }
}
