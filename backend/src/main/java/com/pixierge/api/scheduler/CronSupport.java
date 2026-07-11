package com.pixierge.api.scheduler;

import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CronSupport {

    private CronSupport() {
    }

    public static CronExpression parse(String cronExpression) {
        try {
            return CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, exception);
        }
    }

    public static ZoneId zoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone, exception);
        }
    }

    public static OffsetDateTime nextRunAt(String cronExpression, String timezone, OffsetDateTime from) {
        CronExpression cron = parse(cronExpression);
        ZoneId zone = zoneId(timezone);
        ZonedDateTime next = cron.next(from.atZoneSameInstant(zone));
        return next == null ? null : next.toOffsetDateTime();
    }
}
