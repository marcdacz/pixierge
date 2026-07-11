package com.pixierge.api.scheduler;

import com.pixierge.api.assets.AssetService;
import com.pixierge.api.libraries.LibraryRepository;
import com.pixierge.api.scans.ScanService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;

import static com.pixierge.api.scheduler.SchedulerConstants.DEFAULT_TIMEZONE;
import static com.pixierge.api.scheduler.SchedulerConstants.LIBRARY_SCAN_CONCURRENCY_KEY;
import static com.pixierge.api.scheduler.SchedulerConstants.LIBRARY_SCAN_CRON;
import static com.pixierge.api.scheduler.SchedulerConstants.LIBRARY_SCAN_TIMEOUT_SECONDS;
import static com.pixierge.api.scheduler.SchedulerConstants.METADATA_SCAN_CONCURRENCY_KEY;
import static com.pixierge.api.scheduler.SchedulerConstants.METADATA_SCAN_CRON;
import static com.pixierge.api.scheduler.SchedulerConstants.METADATA_SCAN_TIMEOUT_SECONDS;
import static com.pixierge.api.scheduler.SchedulerConstants.MILLIS_PER_SECOND;

@Configuration
public class CoreSchedulerJobsConfig {

    public static final String LIBRARY_SCAN_JOB_KEY = "core.library-scan";
    public static final String METADATA_SCAN_JOB_KEY = "core.metadata-scan";

    @Bean
    SchedulerJobDefinition libraryScanJobDefinition(ScanService scanService, LibraryRepository libraryRepository) {
        return new SchedulerJobDefinition(
                LIBRARY_SCAN_JOB_KEY,
                "Library scan",
                "Scans all active libraries using the same reconciliation path as manual scans.",
                LIBRARY_SCAN_CRON,
                DEFAULT_TIMEZONE,
                true,
                LIBRARY_SCAN_TIMEOUT_SECONDS,
                LIBRARY_SCAN_CONCURRENCY_KEY,
                job -> {
                    int started = 0;
                    int skippedBusy = 0;
                    int failed = 0;
                    for (LibraryRepository.LibraryRecord library : libraryRepository.listLibraries()) {
                        if (!"active".equals(library.status())) {
                            continue;
                        }
                        try {
                            scanService.scanLibrary(library.id(), null);
                            started++;
                        } catch (ResponseStatusException exception) {
                            if (exception.getStatusCode().value() == 409) {
                                skippedBusy++;
                            } else {
                                failed++;
                            }
                        } catch (RuntimeException exception) {
                            failed++;
                        }
                    }
                    return new SchedulerJobResult(
                            "{\"librariesStarted\":" + started
                                    + ",\"librariesSkippedBusy\":" + skippedBusy
                                    + ",\"librariesFailed\":" + failed + "}"
                    );
                }
        );
    }

    @Bean
    SchedulerJobDefinition metadataScanJobDefinition(AssetService assetService) {
        return new SchedulerJobDefinition(
                METADATA_SCAN_JOB_KEY,
                "Metadata scan",
                "Extracts metadata for assets that still need extraction.",
                METADATA_SCAN_CRON,
                DEFAULT_TIMEZONE,
                true,
                METADATA_SCAN_TIMEOUT_SECONDS,
                METADATA_SCAN_CONCURRENCY_KEY,
                job -> {
                    long deadline = System.currentTimeMillis() + job.timeoutSeconds() * MILLIS_PER_SECOND;
                    int processed = 0;
                    int failed = 0;
                    while (System.currentTimeMillis() < deadline) {
                        var batch = assetService.backfillMetadata();
                        processed += batch.processedCount();
                        failed += batch.failedCount();
                        if (batch.processedCount() == 0) {
                            break;
                        }
                    }
                    return new SchedulerJobResult(
                            "{\"processedCount\":" + processed + ",\"failedCount\":" + failed + "}"
                    );
                }
        );
    }
}
