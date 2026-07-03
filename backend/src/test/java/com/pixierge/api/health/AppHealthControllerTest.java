package com.pixierge.api.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppHealthControllerTest {

    @Test
    void returnsReadyHealth() throws Exception {
        MockMvc mockMvc = mockMvcFor(new HealthResponse("ok", "ready", "pixierge-api"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("ready"))
                .andExpect(jsonPath("$.app").value("pixierge-api"));
    }

    @Test
    void returnsUnavailableWhenDatabaseIsNotReady() throws Exception {
        MockMvc mockMvc = mockMvcFor(new HealthResponse("degraded", "unavailable", "pixierge-api"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.database").value("unavailable"))
                .andExpect(jsonPath("$.app").value("pixierge-api"));
    }

    private MockMvc mockMvcFor(HealthResponse healthResponse) {
        return MockMvcBuilders
                .standaloneSetup(new AppHealthController(new StubHealthService(healthResponse)))
                .build();
    }

    private static class StubHealthService extends AppHealthService {

        private final HealthResponse healthResponse;

        StubHealthService(HealthResponse healthResponse) {
            super(null);
            this.healthResponse = healthResponse;
        }

        @Override
        public HealthResponse currentHealth() {
            return healthResponse;
        }
    }
}
