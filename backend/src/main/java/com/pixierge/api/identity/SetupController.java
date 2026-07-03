package com.pixierge.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final AuthService authService;

    public SetupController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    SetupStatusResponse status() {
        return new SetupStatusResponse(authService.setupRequired());
    }

    @PostMapping("/admin")
    AuthResponse createFirstAdmin(
            @RequestBody CreateAdminRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthService.CreatedSession session = authService.createFirstAdmin(request);
        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                SessionCookieSupport.createSessionCookie(session.sessionToken(), servletRequest.isSecure())
        );
        return AuthResponse.from(session.user());
    }
}
