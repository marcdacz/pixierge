package com.pixierge.api.scans;

import com.pixierge.api.libraries.LibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.SyncTaskExecutor;
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

        service(scanRepository, new FakeFileHasher(file)).executeScan(UUID.randomUUID(), library, null);

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

        service(scanRepository, new FakeFileHasher()).executeScan(UUID.randomUUID(), library, null);

        assertThat(scanRepository.markedStatuses).containsExactly(assetFileId + ":missing");
        assertThat(scanRepository.completedCounts.missingCount()).isEqualTo(1);
        assertThat(scanRepository.completedCounts.addedCount()).isEqualTo(1);
    }

    private ScanService service(FakeScanRepository scanRepository, FileHasher fileHasher) {
        return new ScanService(
                new FakeLibraryRepository(),
                scanRepository,
                fileHasher,
                new SyncTaskExecutor(),
                new ImmediateTransactionTemplate()
        );
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

        FakeLibraryRepository() {
            super(null);
        }

        @Override
        public List<GlobalExclusionPatternRecord> listGlobalExclusionPatterns() {
            return List.of();
        }
    }

    private static class FakeScanRepository extends ScanRepository {

        private final List<AssetFileRecord> activeFiles;
        private final Map<UUID, UUID> seenInRun = new java.util.HashMap<>();
        private final List<String> markedStatuses = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private ScanCounts completedCounts;

        FakeScanRepository(List<AssetFileRecord> activeFiles) {
            super(null);
            this.activeFiles = activeFiles;
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
        void updateActiveFileSeen(
                UUID assetFileId,
                long sizeBytes,
                OffsetDateTime modifiedAt,
                UUID scanRunId,
                OffsetDateTime now
        ) {
            seenInRun.put(assetFileId, scanRunId);
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
            return Optional.empty();
        }

        @Override
        UUID createAsset(String contentHash, String mediaType, OffsetDateTime now) {
            return UUID.randomUUID();
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
            seenInRun.put(id, scanRunId);
            return id;
        }

        @Override
        void markStatus(UUID assetFileId, String status, UUID replacedByFileId) {
            markedStatuses.add(assetFileId + ":" + status);
        }

        @Override
        void createError(UUID scanRunId, UUID libraryId, UUID rootId, String path, String errorCode, String message) {
            errors.add(errorCode + ":" + message);
        }

        @Override
        void updateScanRunProgress(UUID scanRunId, ScanCounts counts) {
        }

        @Override
        void completeScanRun(UUID scanRunId, ScanCounts counts, OffsetDateTime completedAt) {
            completedCounts = counts;
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
