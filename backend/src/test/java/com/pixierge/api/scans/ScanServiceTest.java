package com.pixierge.api.scans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobRepository;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.libraries.LibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScanServiceTest {

    @TempDir
    private Path tempDir;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void excludedExistingFilesStayActiveInsteadOfBeingMarkedMissing() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID assetFileId = UUID.randomUUID();
        Path ignoredFile = Files.createDirectories(tempDir.resolve("ignored")).resolve("skip.jpg");
        Files.writeString(ignoredFile, "skip");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of(
                new LibraryRepository.LibraryExclusionPatternRecord(UUID.randomUUID(), libraryId, "**/ignored/**", OffsetDateTime.now())
        ));
        FakeScanRepository scanRepository = new FakeScanRepository(List.of(
                assetFile(assetFileId, assetId, libraryId, rootId, ignoredFile)
        ));

        service(scanRepository, new FakeFileHasher()).executeScan(UUID.randomUUID(), library, null);

        assertThat(scanRepository.markedStatuses).isEmpty();
        assertThat(scanRepository.completedCounts.missingCount()).isZero();
        assertThat(scanRepository.completedCounts.errorCount()).isZero();
    }

    @Test
    void unchangedPathSkipsHashing() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID assetFileId = UUID.randomUUID();
        Path file = tempDir.resolve("same.jpg");
        Files.writeString(file, "same-content");
        OffsetDateTime modifiedAt = OffsetDateTime.parse("2026-07-04T12:00:00Z");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(modifiedAt.toInstant()));
        long size = Files.size(file);
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        ScanRepository.AssetFileRecord activeFile = new ScanRepository.AssetFileRecord(
                assetFileId,
                assetId,
                libraryId,
                rootId,
                file.toString(),
                file.toAbsolutePath().normalize().toString(),
                file.getFileName().toString(),
                size,
                modifiedAt,
                "confirmed-hash",
                "active"
        );
        ThrowingFileHasher fileHasher = new ThrowingFileHasher();
        FakeScanRepository scanRepository = new FakeScanRepository(List.of(activeFile));

        service(scanRepository, fileHasher).executeScan(UUID.randomUUID(), library, null);

        assertThat(fileHasher.invocations).isZero();
        assertThat(scanRepository.completedCounts.unchangedCount()).isEqualTo(1);
        assertThat(scanRepository.completedCounts.missingCount()).isZero();
    }

    @Test
    void failedFileHashDoesNotLetMissingSweepChangePriorState() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID assetFileId = UUID.randomUUID();
        Path file = tempDir.resolve("broken.jpg");
        Files.writeString(file, "broken");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        ScanRepository.AssetFileRecord activeFile = assetFile(assetFileId, assetId, libraryId, rootId, file);
        FakeScanRepository scanRepository = new FakeScanRepository(List.of(activeFile));
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        ScanService service = service(new FakeLibraryRepository(library), scanRepository, new FakeFileHasher(file), backgroundJobService);

        service.executeScan(UUID.randomUUID(), library, null);
        runIdentityJobs(service, backgroundJobService);

        assertThat(scanRepository.markedStatuses).isEmpty();
        assertThat(scanRepository.errors).containsExactly("file_failed:cannot read");
        assertThat(scanRepository.completedCounts.scannedFileCount()).isEqualTo(1);
        assertThat(scanRepository.completedCounts.missingCount()).isZero();
        assertThat(scanRepository.completedCounts.errorCount()).isEqualTo(1);
    }

    @Test
    void activeFileNotObservedDuringScanIsMarkedMissing() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID assetFileId = UUID.randomUUID();
        Path present = tempDir.resolve("present.jpg");
        Files.writeString(present, "present");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        ScanRepository.AssetFileRecord missingCandidate = new ScanRepository.AssetFileRecord(
                assetFileId,
                assetId,
                libraryId,
                rootId,
                tempDir.resolve("missing.jpg").toString(),
                tempDir.resolve("missing.jpg").toAbsolutePath().normalize().toString(),
                "missing.jpg",
                4,
                OffsetDateTime.now(),
                "confirmed-hash",
                "active"
        );
        FakeScanRepository scanRepository = new FakeScanRepository(List.of(missingCandidate));
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        ScanService service = service(new FakeLibraryRepository(library), scanRepository, new FakeFileHasher(), backgroundJobService);

        service.executeScan(UUID.randomUUID(), library, null);
        runIdentityJobs(service, backgroundJobService);

        assertThat(scanRepository.markedStatuses).containsExactly(assetFileId + ":missing");
        assertThat(scanRepository.completedCounts.missingCount()).isEqualTo(1);
        assertThat(scanRepository.completedCounts.addedCount()).isEqualTo(1);
    }

    @Test
    void scanLibraryCreatesQueuedRunAndCatalogJob() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        FakeScanRepository scanRepository = new FakeScanRepository(List.of());
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();

        ScanRunResponse response = service(
                new FakeLibraryRepository(library),
                scanRepository,
                new FakeFileHasher(),
                backgroundJobService
        ).scanLibrary(libraryId, requestedBy);

        BackgroundJobCreate job = backgroundJobService.enqueuedJobs.getFirst();
        ScanCatalogJobPayload payload = objectMapper.readValue(job.payloadJson(), ScanCatalogJobPayload.class);
        assertThat(response.status()).isEqualTo("queued");
        assertThat(scanRepository.scanRuns).containsKey(response.id());
        assertThat(job.jobType()).isEqualTo(ScanJobTypes.LIBRARY_CATALOG_ROOT);
        assertThat(job.concurrencyKey()).isEqualTo("library:" + libraryId);
        assertThat(job.dedupeKey()).isEqualTo(ScanJobTypes.LIBRARY_CATALOG_ROOT + ":" + response.id());
        assertThat(payload.scanRunId()).isEqualTo(response.id());
        assertThat(payload.libraryId()).isEqualTo(libraryId);
        assertThat(payload.rootId()).isNull();
    }

    @Test
    void identityWorkIsEnqueuedInBatches() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID scanRunId = UUID.randomUUID();
        Files.writeString(tempDir.resolve("one.jpg"), "one");
        Files.writeString(tempDir.resolve("two.jpg"), "two");
        Files.writeString(tempDir.resolve("three.jpg"), "three");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        ScanService service = service(
                new FakeLibraryRepository(library),
                new FakeScanRepository(List.of()),
                new FakeFileHasher(),
                backgroundJobService,
                2
        );

        service.executeScan(scanRunId, library, null);

        List<BackgroundJobCreate> identityJobs = backgroundJobService.enqueuedJobs.stream()
                .filter(job -> ScanJobTypes.ASSET_IDENTITY_BACKFILL.equals(job.jobType()))
                .toList();
        ScanIdentityJobPayload firstPayload = objectMapper.readValue(
                identityJobs.getFirst().payloadJson(),
                ScanIdentityJobPayload.class
        );
        ScanIdentityJobPayload secondPayload = objectMapper.readValue(
                identityJobs.get(1).payloadJson(),
                ScanIdentityJobPayload.class
        );
        assertThat(identityJobs).hasSize(2);
        assertThat(identityJobs.getFirst().concurrencyKey())
                .isEqualTo(ScanJobTypes.ASSET_IDENTITY_BACKFILL + ":" + scanRunId + ":batch:0");
        assertThat(identityJobs.get(1).dedupeKey())
                .isEqualTo(ScanJobTypes.ASSET_IDENTITY_BACKFILL + ":" + scanRunId + ":batch:1");
        assertThat(firstPayload.identityItems()).hasSize(2);
        assertThat(secondPayload.identityItems()).hasSize(1);
    }

    @Test
    void newFileWithContentHashFromAnotherLibraryUsesExistingAsset() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID sharedAssetId = UUID.randomUUID();
        Path file = tempDir.resolve("shared.jpg");
        Files.writeString(file, "same bytes");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        FakeScanRepository scanRepository = new FakeScanRepository(List.of());
        scanRepository.assetsByHash.put("full", sharedAssetId);
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        ScanService service = service(new FakeLibraryRepository(library), scanRepository, new FakeFileHasher(), backgroundJobService);

        service.executeScan(UUID.randomUUID(), library, null);
        runIdentityJobs(service, backgroundJobService);

        assertThat(scanRepository.createdAssetFileAssetIds).contains(sharedAssetId);
        assertThat(scanRepository.updatedAssetContentHashes).doesNotContain("full");
        assertThat(scanRepository.markedStatuses).anySatisfy(status ->
                assertThat(status).endsWith(":superseded")
        );
    }

    @Test
    void subtreeCatalogScanDoesNotMarkFilesOutsideSubtreeMissing() throws Exception {
        UUID libraryId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID outsideAssetId = UUID.randomUUID();
        UUID outsideAssetFileId = UUID.randomUUID();
        Path incoming = Files.createDirectories(tempDir.resolve("incoming"));
        Path newFile = incoming.resolve("new.jpg");
        Path outside = tempDir.resolve("outside.jpg");
        Files.writeString(newFile, "new");
        LibraryRepository.LibraryRootRecord root = root(libraryId, rootId, tempDir);
        LibraryRepository.LibraryRecord library = library(libraryId, root, List.of());
        ScanRepository.AssetFileRecord outsideFile = new ScanRepository.AssetFileRecord(
                outsideAssetFileId,
                outsideAssetId,
                libraryId,
                rootId,
                outside.toString(),
                outside.toAbsolutePath().normalize().toString(),
                outside.getFileName().toString(),
                4,
                OffsetDateTime.now(),
                "confirmed-hash",
                "active"
        );
        FakeScanRepository scanRepository = new FakeScanRepository(List.of(outsideFile));
        RecordingBackgroundJobService backgroundJobService = new RecordingBackgroundJobService();
        ScanService service = service(new FakeLibraryRepository(library), scanRepository, new FakeFileHasher(), backgroundJobService);
        UUID scanRunId = scanRepository.createScanRun(libraryId, rootId, null, OffsetDateTime.now());

        service.executeCatalogJob(new ScanCatalogJobPayload(
                scanRunId,
                libraryId,
                rootId,
                incoming.toAbsolutePath().normalize().toString()
        ), UUID.randomUUID());
        runIdentityJobs(service, backgroundJobService);

        assertThat(scanRepository.markedStatuses).doesNotContain(outsideAssetFileId + ":missing");
        assertThat(scanRepository.completedCounts.missingCount()).isZero();
        assertThat(scanRepository.completedCounts.addedCount()).isEqualTo(1);
    }

    private ScanService service(FakeScanRepository scanRepository, FileHasher fileHasher) {
        return service(new FakeLibraryRepository(), scanRepository, fileHasher, new RecordingBackgroundJobService());
    }

    private ScanService service(
            FakeScanRepository scanRepository,
            FileHasher fileHasher,
            BackgroundJobService backgroundJobService
    ) {
        return service(new FakeLibraryRepository(), scanRepository, fileHasher, backgroundJobService);
    }

    private ScanService service(
            FakeLibraryRepository libraryRepository,
            FakeScanRepository scanRepository,
            FileHasher fileHasher,
            BackgroundJobService backgroundJobService
    ) {
        return service(libraryRepository, scanRepository, fileHasher, backgroundJobService, 100);
    }

    private ScanService service(
            FakeLibraryRepository libraryRepository,
            FakeScanRepository scanRepository,
            FileHasher fileHasher,
            BackgroundJobService backgroundJobService,
            int identityBatchSize
    ) {
        return new ScanService(
                libraryRepository,
                scanRepository,
                fileHasher,
                backgroundJobService,
                new ImmediateTransactionTemplate(),
                objectMapper,
                identityBatchSize
        );
    }

    private void runIdentityJobs(ScanService service, RecordingBackgroundJobService backgroundJobService) throws Exception {
        for (RecordingBackgroundJobService.RecordedJob job : backgroundJobService.recordedJobs) {
            if (!ScanJobTypes.ASSET_IDENTITY_BACKFILL.equals(job.create().jobType())) {
                continue;
            }
            ScanIdentityJobPayload payload = objectMapper.readValue(
                    job.create().payloadJson(),
                    ScanIdentityJobPayload.class
            );
            service.executeIdentityJob(payload, job.id());
            backgroundJobService.complete(job.id());
            service.tryCompleteIdentityScan(payload);
        }
    }

    private LibraryRepository.LibraryRecord library(
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            List<LibraryRepository.LibraryExclusionPatternRecord> exclusions
    ) {
        return new LibraryRepository.LibraryRecord(
                libraryId,
                "Family Photos",
                "active",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                List.of(root),
                exclusions
        );
    }

    private LibraryRepository.LibraryRootRecord root(UUID libraryId, UUID rootId, Path path) {
        return new LibraryRepository.LibraryRootRecord(
                rootId,
                libraryId,
                path.toString(),
                path.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
    }

    private ScanRepository.AssetFileRecord assetFile(
            UUID assetFileId,
            UUID assetId,
            UUID libraryId,
            UUID rootId,
            Path path
    ) {
        return new ScanRepository.AssetFileRecord(
                assetFileId,
                assetId,
                libraryId,
                rootId,
                path.toString(),
                path.toAbsolutePath().normalize().toString(),
                path.getFileName().toString(),
                4,
                OffsetDateTime.now(),
                "sha256",
                "active"
        );
    }

    private static class FakeLibraryRepository extends LibraryRepository {

        private final LibraryRecord library;

        FakeLibraryRepository() {
            this(null);
        }

        FakeLibraryRepository(LibraryRecord library) {
            super(null);
            this.library = library;
        }

        @Override
        public List<GlobalExclusionPatternRecord> listGlobalExclusionPatterns() {
            return List.of();
        }

        @Override
        public Optional<LibraryRecord> findLibrary(UUID libraryId) {
            if (library != null && library.id().equals(libraryId)) {
                return Optional.of(library);
            }
            return Optional.empty();
        }
    }

    private static class FakeScanRepository extends ScanRepository {

        private final List<AssetFileRecord> activeFiles;
        private final Map<UUID, UUID> seenInRun = new java.util.HashMap<>();
        private final Map<UUID, ScanRunRecord> scanRuns = new java.util.HashMap<>();
        private final Map<String, UUID> assetsByHash = new java.util.HashMap<>();
        private final List<UUID> createdAssetFileAssetIds = new ArrayList<>();
        private final List<String> updatedAssetContentHashes = new ArrayList<>();
        private final List<String> markedStatuses = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final java.util.Set<UUID> catalogCompleted = new java.util.HashSet<>();
        private ScanCounts currentCounts = new ScanCounts();
        private ScanCounts completedCounts;

        FakeScanRepository(List<AssetFileRecord> activeFiles) {
            super(null);
            this.activeFiles = new ArrayList<>(activeFiles);
        }

        @Override
        UUID createScanRun(UUID libraryId, UUID rootId, UUID requestedBy, OffsetDateTime now) {
            UUID scanRunId = UUID.randomUUID();
            scanRuns.put(scanRunId, new ScanRunRecord(
                    scanRunId,
                    libraryId,
                    rootId,
                    "queued",
                    now,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            ));
            return scanRunId;
        }

        @Override
        boolean hasActiveScanRun(UUID libraryId) {
            return scanRuns.values().stream()
                    .filter(run -> run.libraryId().equals(libraryId))
                    .anyMatch(run -> "queued".equals(run.status()) || "running".equals(run.status()));
        }

        @Override
        void markScanRunRunning(UUID scanRunId, OffsetDateTime startedAt) {
            ScanRunRecord run = scanRuns.get(scanRunId);
            if (run == null) {
                return;
            }
            scanRuns.put(scanRunId, new ScanRunRecord(
                    run.id(),
                    run.libraryId(),
                    run.rootId(),
                    "running",
                    startedAt,
                    run.completedAt(),
                    run.scannedFileCount(),
                    run.addedCount(),
                    run.unchangedCount(),
                    run.movedCount(),
                    run.modifiedCount(),
                    run.duplicateCount(),
                    run.missingCount(),
                    run.reappearedCount(),
                    run.errorCount()
            ));
        }

        @Override
        Optional<ScanRunRecord> findScanRun(UUID scanRunId) {
            ScanRunRecord run = scanRuns.get(scanRunId);
            if (run != null) {
                return Optional.of(run);
            }
            if (!catalogCompleted.contains(scanRunId)) {
                return Optional.empty();
            }
            return Optional.of(new ScanRunRecord(
                    scanRunId,
                    UUID.randomUUID(),
                    null,
                    "running",
                    OffsetDateTime.now(),
                    null,
                    currentCounts.scannedFileCount(),
                    currentCounts.addedCount(),
                    currentCounts.unchangedCount(),
                    currentCounts.movedCount(),
                    currentCounts.modifiedCount(),
                    currentCounts.duplicateCount(),
                    currentCounts.missingCount(),
                    currentCounts.reappearedCount(),
                    currentCounts.errorCount()
            ));
        }

        @Override
        List<ScanErrorRecord> errorsForScan(UUID scanRunId) {
            return List.of();
        }

        @Override
        List<AssetFileRecord> activeFilesNotSeenInRun(UUID libraryId, UUID rootId, UUID scanRunId) {
            return activeFiles.stream()
                    .filter(file -> file.libraryId().equals(libraryId))
                    .filter(file -> file.rootId().equals(rootId))
                    .filter(file -> "active".equals(file.status()))
                    .filter(file -> !scanRunId.equals(seenInRun.get(file.id())))
                    .toList();
        }

        @Override
        Optional<AssetFileRecord> findActiveFileByPath(UUID libraryId, String normalizedPath) {
            return activeFiles.stream()
                    .filter(file -> file.libraryId().equals(libraryId))
                    .filter(file -> file.normalizedPath().equals(normalizedPath))
                    .findFirst();
        }

        @Override
        Map<String, AssetFileRecord> findActiveFilesByPaths(UUID libraryId, List<String> normalizedPaths) {
            Map<String, AssetFileRecord> records = new java.util.HashMap<>();
            for (String normalizedPath : normalizedPaths) {
                findActiveFileByPath(libraryId, normalizedPath)
                        .ifPresent(record -> records.put(normalizedPath, record));
            }
            return records;
        }

        @Override
        Optional<AssetFileRecord> findActiveFileByHash(UUID libraryId, String contentHash) {
            return activeFiles.stream()
                    .filter(file -> file.libraryId().equals(libraryId))
                    .filter(file -> contentHash.equals(file.contentHash()))
                    .filter(file -> "active".equals(file.status()))
                    .findFirst();
        }

        @Override
        Optional<AssetFileRecord> findMissingFileByPathAndHash(UUID libraryId, String normalizedPath, String contentHash) {
            return activeFiles.stream()
                    .filter(file -> file.libraryId().equals(libraryId))
                    .filter(file -> file.normalizedPath().equals(normalizedPath))
                    .filter(file -> contentHash.equals(file.contentHash()))
                    .filter(file -> "missing".equals(file.status()))
                    .findFirst();
        }

        @Override
        void updateActiveFileSeen(
                UUID assetFileId,
                long sizeBytes,
                OffsetDateTime modifiedAt,
                UUID scanRunId,
                OffsetDateTime now
        ) {
            seenInRun.put(assetFileId, scanRunId);
            findFakeAssetFile(assetFileId).ifPresent(file -> replaceAssetFile(assetFileId, new AssetFileRecord(
                    file.id(),
                    file.assetId(),
                    file.libraryId(),
                    file.rootId(),
                    file.path(),
                    file.normalizedPath(),
                    file.fileName(),
                    sizeBytes,
                    modifiedAt,
                    file.contentHash(),
                    file.status()
            )));
        }

        @Override
        void touchAsset(UUID assetId, OffsetDateTime now) {
        }

        @Override
        void createObservations(List<ObservationInsert> observations) {
        }

        @Override
        void createObservation(
                UUID scanRunId,
                UUID libraryId,
                UUID rootId,
                UUID assetId,
                UUID assetFileId,
                String path,
                String normalizedPath,
                Long sizeBytes,
                OffsetDateTime modifiedAt,
                String partialHash,
                String contentHash,
                String result
        ) {
        }

        @Override
        Optional<UUID> findAssetByHash(String contentHash) {
            return Optional.ofNullable(assetsByHash.get(contentHash));
        }

        @Override
        UUID createAsset(String contentHash, String mediaType, OffsetDateTime now) {
            UUID assetId = UUID.randomUUID();
            assetsByHash.put(contentHash, assetId);
            return assetId;
        }

        @Override
        void updateAssetContentHash(UUID assetId, String contentHash, OffsetDateTime now) {
            updatedAssetContentHashes.add(contentHash);
            assetsByHash.put(contentHash, assetId);
        }

        @Override
        void updateAssetFileContentHash(UUID assetFileId, String contentHash) {
            findFakeAssetFile(assetFileId).ifPresent(file -> replaceAssetFile(assetFileId, new AssetFileRecord(
                    file.id(),
                    file.assetId(),
                    file.libraryId(),
                    file.rootId(),
                    file.path(),
                    file.normalizedPath(),
                    file.fileName(),
                    file.sizeBytes(),
                    file.modifiedAt(),
                    contentHash,
                    file.status()
            )));
        }

        @Override
        UUID createAssetFile(
                UUID assetId,
                UUID libraryId,
                UUID rootId,
                String path,
                String normalizedPath,
                String fileName,
                long sizeBytes,
                OffsetDateTime modifiedAt,
                String contentHash,
                UUID scanRunId,
                OffsetDateTime now
        ) {
            UUID id = UUID.randomUUID();
            createdAssetFileAssetIds.add(assetId);
            activeFiles.add(new AssetFileRecord(
                    id,
                    assetId,
                    libraryId,
                    rootId,
                    path,
                    normalizedPath,
                    fileName,
                    sizeBytes,
                    modifiedAt,
                    contentHash,
                    "active"
            ));
            seenInRun.put(id, scanRunId);
            return id;
        }

        @Override
        void markStatus(UUID assetFileId, String status, UUID replacedByFileId) {
            markedStatuses.add(assetFileId + ":" + status);
        }

        @Override
        Optional<AssetFileRecord> findAssetFile(UUID assetFileId) {
            return findFakeAssetFile(assetFileId);
        }

        private Optional<AssetFileRecord> findFakeAssetFile(UUID assetFileId) {
            return activeFiles.stream()
                    .filter(file -> file.id().equals(assetFileId))
                    .findFirst();
        }

        @Override
        void markScanCatalogCompleted(UUID scanRunId, OffsetDateTime completedAt) {
            catalogCompleted.add(scanRunId);
        }

        @Override
        boolean isScanCatalogCompleted(UUID scanRunId) {
            return catalogCompleted.contains(scanRunId);
        }

        @Override
        void incrementScanRunCounts(UUID scanRunId, ScanCounts counts) {
            currentCounts.add(counts);
        }

        @Override
        void completeScanRunFromCurrentCounts(UUID scanRunId, OffsetDateTime completedAt) {
            completedCounts = currentCounts;
        }

        private void replaceAssetFile(UUID assetFileId, AssetFileRecord replacement) {
            for (int index = 0; index < activeFiles.size(); index++) {
                if (activeFiles.get(index).id().equals(assetFileId)) {
                    activeFiles.set(index, replacement);
                    return;
                }
            }
        }

        @Override
        void createError(UUID scanRunId, UUID libraryId, UUID rootId, String path, String errorCode, String message) {
            errors.add(errorCode + ":" + message);
        }

        @Override
        void updateScanRunProgress(UUID scanRunId, ScanCounts counts) {
            currentCounts = counts;
        }

    }

    private static class RecordingBackgroundJobService extends BackgroundJobService {

        private final List<BackgroundJobCreate> enqueuedJobs = new ArrayList<>();
        private final List<RecordedJob> recordedJobs = new ArrayList<>();
        private final java.util.Set<UUID> completedJobIds = new java.util.HashSet<>();

        RecordingBackgroundJobService() {
            super(new BackgroundJobRepository(null), new ImmediateTransactionTemplate());
        }

        @Override
        public UUID enqueue(BackgroundJobCreate create) {
            UUID id = UUID.randomUUID();
            enqueuedJobs.add(create);
            recordedJobs.add(new RecordedJob(id, create));
            return id;
        }

        @Override
        public boolean hasActiveJobs(String jobType, String dedupeKeyPrefix, UUID excludedJobId) {
            return recordedJobs.stream()
                    .filter(job -> !completedJobIds.contains(job.id()))
                    .filter(job -> excludedJobId == null || !job.id().equals(excludedJobId))
                    .map(RecordedJob::create)
                    .filter(job -> job.jobType().equals(jobType))
                    .anyMatch(job -> job.dedupeKey() != null && job.dedupeKey().startsWith(dedupeKeyPrefix));
        }

        private void complete(UUID jobId) {
            completedJobIds.add(jobId);
        }

        private record RecordedJob(UUID id, BackgroundJobCreate create) {
        }
    }

    private static class ThrowingFileHasher extends FileHasher {

        private int invocations;

        @Override
        Hashes hash(Path path) throws IOException {
            invocations++;
            throw new IOException("should not hash");
        }
    }

    private static class FakeFileHasher extends FileHasher {

        private final Path failingPath;

        FakeFileHasher() {
            this(null);
        }

        FakeFileHasher(Path failingPath) {
            this.failingPath = failingPath == null ? null : failingPath.toAbsolutePath().normalize();
        }

        @Override
        Hashes hash(Path path) throws IOException {
            if (failingPath != null && failingPath.equals(path.toAbsolutePath().normalize())) {
                throw new IOException("cannot read");
            }
            return new Hashes("partial", "full");
        }
    }

    private static class ImmediateTransactionTemplate extends TransactionTemplate {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<TransactionStatus> action) {
            action.accept(null);
        }
    }
}
