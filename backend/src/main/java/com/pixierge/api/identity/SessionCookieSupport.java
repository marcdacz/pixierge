package com.pixierge.api.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

public final class SessionCookieSupport {

    private SessionCookieSupport() {
    }

    public static Optional<String> findSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> IdentityConstants.SESSION_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    public static String createSessionCookie(String sessionToken, boolean secure) {
        return ResponseCookie.from(IdentityConstants.SESSION_COOKIE_NAME, sessionToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(AuthService.SESSION_DURATION)
                .build()
                .toString();
    }

    public static String clearSessionCookie(boolean secure) {
        return ResponseCookie.from(IdentityConstants.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }
}
