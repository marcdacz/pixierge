package com.pixierge.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    AuthResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthService.CreatedSession session = authService.login(request);
        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                SessionCookieSupport.createSessionCookie(session.sessionToken(), servletRequest.isSecure())
        );
        return AuthResponse.from(session.user());
    }

    @PostMapping("/logout")
    void logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        String sessionToken = SessionCookieSupport.findSessionToken(servletRequest)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
        authService.logout(sessionToken);
        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                SessionCookieSupport.clearSessionCookie(servletRequest.isSecure())
        );
    }

    @GetMapping("/session")
    AuthResponse session(@AuthenticationPrincipal AuthenticatedUser user) {
        return AuthResponse.from(user);
    }
}
