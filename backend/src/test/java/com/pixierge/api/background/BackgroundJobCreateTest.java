package com.pixierge.api.background;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackgroundJobCreateTest {

    @Test
    void constructorDefaultsBlankPayloadAndMinimumAttempts() {
        OffsetDateTime nextRunAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");

        BackgroundJobCreate create = new BackgroundJobCreate(
                "scan",
                " ",
                5,
                0,
                nextRunAt,
                "library:1",
                "scan:1"
        );

        assertThat(create.payloadJson()).isEqualTo("{}");
        assertThat(create.maxAttempts()).isEqualTo(1);
        assertThat(create.nextRunAt()).isEqualTo(nextRunAt);
    }

    @Test
    void constructorRejectsMissingRequiredKeys() {
        assertThatThrownBy(() -> new BackgroundJobCreate(null, "{}", 0, 1, null, "library:1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jobType is required");
        assertThatThrownBy(() -> new BackgroundJobCreate("scan", "{}", 0, 1, null, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("concurrencyKey is required");
    }
}
