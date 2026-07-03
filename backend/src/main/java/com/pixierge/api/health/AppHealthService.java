package com.pixierge.api.health;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AppHealthService {

    private static final String APP_NAME = "pixierge-api";

    private final AppMetadataRepository appMetadataRepository;

    public AppHealthService(AppMetadataRepository appMetadataRepository) {
        this.appMetadataRepository = appMetadataRepository;
    }

    public HealthResponse currentHealth() {
        try {
            return appMetadataRepository.findValue("schema_marker")
                    .map(marker -> new HealthResponse("ok", "ready", APP_NAME))
                    .orElseGet(() -> unavailable());
        } catch (DataAccessException exception) {
            return unavailable();
        }
    }

    private HealthResponse unavailable() {
        return new HealthResponse("degraded", "unavailable", APP_NAME);
    }
}
