package com.pixierge.api.scheduler;

public final class SchedulerConstants {

    public static final String DEFAULT_TIMEZONE = "UTC";

    public static final String LIBRARY_SCAN_CRON = "0 0 2 * * *";
    public static final String METADATA_SCAN_CRON = "0 30 2 * * *";
    public static final String FILE_ACTIVITY_RETENTION_CRON = "0 0 3 * * *";

    public static final String LIBRARY_SCAN_CONCURRENCY_KEY = "core:library-scan";
    public static final String METADATA_SCAN_CONCURRENCY_KEY = "core:metadata-scan";
    public static final String FILE_ACTIVITY_RETENTION_CONCURRENCY_KEY = "core:file-activity-retention";

    public static final int LIBRARY_SCAN_TIMEOUT_SECONDS = 6 * 60 * 60;
    public static final int METADATA_SCAN_TIMEOUT_SECONDS = 2 * 60 * 60;
    public static final int FILE_ACTIVITY_RETENTION_TIMEOUT_SECONDS = 10 * 60;

    public static final long MILLIS_PER_SECOND = 1_000L;
    public static final String DEFAULT_POLL_INTERVAL_MS = "15000";

    private SchedulerConstants() {
    }
}
