package com.pixierge.api.background;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.filesystem.FilesystemWatcherHealth;
import com.pixierge.api.scans.ScanIdentityJobPayload;
import com.pixierge.api.scans.ScanJobTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
class BackgroundWorkController {

    private final BackgroundJobService backgroundJobService;
    private final BackgroundActivityRepository activityRepository;
    private final FilesystemWatcherHealth watcherHealth;
    private final ObjectMapper objectMapper;
    private final int maxConcurrentJobs;
    private final int identityBatchSize;
    private final int claimBatchSize;
    private final long pollIntervalMs;

    BackgroundWorkController(
            BackgroundJobService backgroundJobService,
            BackgroundActivityRepository activityRepository,
            FilesystemWatcherHealth watcherHealth,
            ObjectMapper objectMapper,
            @Value("${pixierge.background-jobs.max-concurrent-jobs:2}") int maxConcurrentJobs,
            @Value("${pixierge.background-jobs.identity-batch-size:100}") int identityBatchSize,
            @Value("${pixierge.background-jobs.claim-batch-size:25}") int claimBatchSize,
            @Value("${pixierge.background-jobs.poll-interval-ms:2000}") long pollIntervalMs
    ) {
        this.backgroundJobService = backgroundJobService;
        this.activityRepository = activityRepository;
        this.watcherHealth = watcherHealth;
        this.objectMapper = objectMapper;
        this.maxConcurrentJobs = Math.max(1, maxConcurrentJobs);
        this.identityBatchSize = Math.max(1, identityBatchSize);
        this.claimBatchSize = Math.max(1, claimBatchSize);
        this.pollIntervalMs = Math.max(1L, pollIntervalMs);
    }

    @GetMapping("/api/admin/background/health")
    BackgroundWorkHealthResponse health() {
        return new BackgroundWorkHealthResponse(
                backgroundJobService.summarizeByTypeAndStatus(),
                backgroundJobService.latestProblemJobs(10),
                watcherHealth.snapshot()
        );
    }

    @GetMapping("/api/admin/background/activity")
    BackgroundWorkActivityResponse activity(@RequestParam(defaultValue = "100") int limit) {
        int boundedLimit = Math.min(200, Math.max(1, limit));
        List<BackgroundJobRecord> jobs = backgroundJobService.latestJobs(50);
        List<BackgroundActivityJobSummary> jobSummaries = jobs.stream()
                .map(this::toJobSummary)
                .toList();
        List<BackgroundFileActivitySummary> files = new ArrayList<>();
        for (BackgroundJobRecord job : jobs) {
            if (files.size() >= boundedLimit) {
                break;
            }
            files.addAll(activeFileRows(job, boundedLimit - files.size()));
        }
        if (files.size() < boundedLimit) {
            files.addAll(activityRepository.recentFileActivity(boundedLimit - files.size()).stream()
                    .map(this::toFileSummary)
                    .toList());
        }
        return new BackgroundWorkActivityResponse(jobSummaries, files.stream().limit(boundedLimit).toList());
    }

    @GetMapping("/api/admin/background/files")
    BackgroundFileActivityPage files(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String updatedFrom,
            @RequestParam(required = false) String updatedTo
    ) {
        OffsetDateTime from = parseUpdatedFrom(updatedFrom);
        OffsetDateTime toExclusive = parseUpdatedToExclusive(updatedTo);
        String normalizedQuery = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Set<String> normalizedStatuses = normalizeStatuses(status);

        OffsetDateTime filterFrom = from;
        OffsetDateTime filterTo = toExclusive;
        List<BackgroundFileActivitySummary> activeMatches = backgroundJobService.latestJobs(50).stream()
                .flatMap(job -> activeFileRows(job, Integer.MAX_VALUE).stream())
                .filter(item -> BackgroundFileActivityPager.matches(
                        item,
                        normalizedQuery,
                        normalizedStatuses,
                        filterFrom,
                        filterTo
                ))
                .toList();

        return BackgroundFileActivityPager.page(
                activeMatches,
                page,
                pageSize,
                (offset, limit) -> activityRepository.searchFileActivity(
                        normalizedQuery,
                        normalizedStatuses,
                        filterFrom,
                        filterTo,
                        offset,
                        limit
                )
        );
    }

    @GetMapping("/api/admin/background/config")
    BackgroundWorkConfigResponse config() {
        return new BackgroundWorkConfigResponse(
                maxConcurrentJobs,
                identityBatchSize,
                claimBatchSize,
                pollIntervalMs
        );
    }

    private BackgroundActivityJobSummary toJobSummary(BackgroundJobRecord job) {
        return new BackgroundActivityJobSummary(
                job.id(),
                job.jobType(),
                job.status(),
                batchLabel(job),
                fileCount(job),
                job.attempts(),
                job.maxAttempts(),
                job.lockedBy(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private List<BackgroundFileActivitySummary> activeFileRows(BackgroundJobRecord job, int limit) {
        if (!ScanJobTypes.ASSET_IDENTITY_BACKFILL.equals(job.jobType())
                || !(BackgroundJobRepository.STATUS_PENDING.equals(job.status())
                || BackgroundJobRepository.STATUS_RUNNING.equals(job.status()))) {
            return List.of();
        }
        return identityPayload(job).identityItems().stream()
                .limit(Math.max(0, limit))
                .map(item -> new BackgroundFileActivitySummary(
                        item.path(),
                        fileName(item.path()),
                        BackgroundJobRepository.STATUS_RUNNING.equals(job.status()) ? "processing" : "pending",
                        job.id(),
                        batchLabel(job),
                        job.updatedAt(),
                        null
                ))
                .toList();
    }

    private BackgroundFileActivitySummary toFileSummary(BackgroundFileActivityRow row) {
        return new BackgroundFileActivitySummary(
                row.path(),
                fileName(row.path()),
                row.result(),
                null,
                null,
                row.observedAt(),
                row.message()
        );
    }

    private int fileCount(BackgroundJobRecord job) {
        if (!ScanJobTypes.ASSET_IDENTITY_BACKFILL.equals(job.jobType())) {
            return 0;
        }
        return identityPayload(job).identityItems().size();
    }

    private ScanIdentityJobPayload identityPayload(BackgroundJobRecord job) {
        try {
            return objectMapper.readValue(job.payloadJson(), ScanIdentityJobPayload.class);
        } catch (JsonProcessingException exception) {
            return new ScanIdentityJobPayload(null, null, null, List.of(), null);
        }
    }

    private String batchLabel(BackgroundJobRecord job) {
        String key = job.concurrencyKey();
        if (ScanJobTypes.ASSET_IDENTITY_BACKFILL.equals(job.jobType()) && key != null && key.contains(":batch:")) {
            return "identity batch " + key.substring(key.lastIndexOf(":batch:") + ":batch:".length());
        }
        if (ScanJobTypes.LIBRARY_CATALOG_ROOT.equals(job.jobType())) {
            return "catalog root";
        }
        if (ScanJobTypes.LIBRARY_CATALOG_SUBTREE.equals(job.jobType())) {
            return "catalog subtree";
        }
        return job.jobType();
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "Unknown file";
        }
        Path fileName = Path.of(path).getFileName();
        return fileName == null ? path : fileName.toString();
    }

    private static Set<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        Set<String> normalized = statuses.stream()
                .flatMap(status -> Stream.of(status.split(",")))
                .map(String::trim)
                .filter(status -> !status.isEmpty())
                .map(status -> status.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? null : normalized;
    }

    private static OffsetDateTime parseUpdatedFrom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(trimmed);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid updatedFrom: " + value, exception);
        }
    }

    private static OffsetDateTime parseUpdatedToExclusive(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(trimmed);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid updatedTo: " + value, exception);
        }
    }
}
