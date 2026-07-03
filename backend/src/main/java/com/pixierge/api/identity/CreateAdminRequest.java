package com.pixierge.api.identity;

public record CreateAdminRequest(String email, String displayName, String password) {
}
