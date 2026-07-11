package com.pixierge.api.scheduler;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronSupportTest {

    @Test
    void parsesValidCronAndComputesNextRun() {
        OffsetDateTime from = OffsetDateTime.of(2026, 7, 11, 1, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime next = CronSupport.nextRunAt(SchedulerConstants.LIBRARY_SCAN_CRON, SchedulerConstants.DEFAULT_TIMEZONE, from);
        assertThat(next).isEqualTo(OffsetDateTime.of(2026, 7, 11, 2, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void rejectsInvalidCron() {
        assertThatThrownBy(() -> CronSupport.parse("not-a-cron"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cron expression");
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThatThrownBy(() -> CronSupport.zoneId("Not/AZone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
    }
}
