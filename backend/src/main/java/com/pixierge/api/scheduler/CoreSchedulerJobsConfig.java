package com.pixierge.api.scheduler;

import com.pixierge.api.assets.MetadataEnrichmentService;
import com.pixierge.api.background.FileActivityService;
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
import static com.pixierge.api.scheduler.SchedulerConstants.FILE_ACTIVITY_RETENTION_CRON;
import static com.pixierge.api.scheduler.SchedulerConstants.FILE_ACTIVITY_RETENTION_CONCURRENCY_KEY;
import static com.pixierge.api.scheduler.SchedulerConstants.FILE_ACTIVITY_RETENTION_TIMEOUT_SECONDS;

@Configuration
public class CoreSchedulerJobsConfig {

    public static final String LIBRARY_SCAN_JOB_KEY = "core.library-scan";
    public static final String METADATA_SCAN_JOB_KEY = "core.metadata-scan";
    public static final String FILE_ACTIVITY_RETENTION_JOB_KEY = "core.file-activity-retention";

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
    SchedulerJobDefinition metadataScanJobDefinition(MetadataEnrichmentService metadataEnrichmentService) {
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
                    var batch = metadataEnrichmentService.enqueueMetadataBackfill();
                    return new SchedulerJobResult(
                            "{\"processedCount\":" + batch.processedCount() + ",\"failedCount\":" + batch.failedCount() + "}"
                    );
                }
        );
    }

    @Bean
    SchedulerJobDefinition fileActivityRetentionJobDefinition(FileActivityService fileActivityService) {
        return new SchedulerJobDefinition(
                FILE_ACTIVITY_RETENTION_JOB_KEY,
                "File activity retention",
                "Deletes file activity history older than the configured retention period.",
                FILE_ACTIVITY_RETENTION_CRON,
                DEFAULT_TIMEZONE,
                true,
                FILE_ACTIVITY_RETENTION_TIMEOUT_SECONDS,
                FILE_ACTIVITY_RETENTION_CONCURRENCY_KEY,
                job -> new SchedulerJobResult("{\"deletedCount\":" + fileActivityService.deleteExpired() + "}")
        );
    }
}
