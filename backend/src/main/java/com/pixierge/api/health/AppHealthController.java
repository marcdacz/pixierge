package com.pixierge.api.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class AppHealthController {

    private final AppHealthService appHealthService;

    public AppHealthController(AppHealthService appHealthService) {
        this.appHealthService = appHealthService;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = appHealthService.currentHealth();
        HttpStatus status = "ok".equals(response.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
