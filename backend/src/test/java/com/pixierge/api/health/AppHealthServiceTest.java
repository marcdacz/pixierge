package com.pixierge.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

class AppHealthServiceTest {

  @Test
  void currentHealthIsReadyWhenSchemaMarkerExists() {
    AppHealthService service =
        new AppHealthService(new StubMetadataRepository(Optional.of("v1"), false));

    HealthResponse response = service.currentHealth();

    assertThat(response).isEqualTo(new HealthResponse("ok", "ready", "pixierge-api"));
  }

  @Test
  void currentHealthIsUnavailableWhenSchemaMarkerIsMissing() {
    AppHealthService service =
        new AppHealthService(new StubMetadataRepository(Optional.empty(), false));

    HealthResponse response = service.currentHealth();

    assertThat(response).isEqualTo(new HealthResponse("degraded", "unavailable", "pixierge-api"));
  }

  @Test
  void currentHealthIsUnavailableWhenRepositoryFails() {
    AppHealthService service =
        new AppHealthService(new StubMetadataRepository(Optional.empty(), true));

    HealthResponse response = service.currentHealth();

    assertThat(response).isEqualTo(new HealthResponse("degraded", "unavailable", "pixierge-api"));
  }

  private static final class StubMetadataRepository extends AppMetadataRepository {

    private final Optional<String> marker;
    private final boolean fails;

    private StubMetadataRepository(Optional<String> marker, boolean fails) {
      super(null);
      this.marker = marker;
      this.fails = fails;
    }

    @Override
    public Optional<String> findValue(String key) {
      assertThat(key).isEqualTo("schema_marker");
      if (fails) {
        throw new DataRetrievalFailureException("database unavailable");
      }
      return marker;
    }
  }
}
