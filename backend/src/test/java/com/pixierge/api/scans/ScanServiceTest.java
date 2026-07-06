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
        assertThat(scanRepository.completedCounts.scannedFileCount()).isZero();
        assertThat(scanRepository.completedCounts.missingCount()).isZero();
        assertThat(scanRepository.completedCounts.errorCount()).isEqualTo(1);
    }

    private ScanService service(FakeScanRepository scanRepository, FakeFileHasher fileHasher) {
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
        private final List<String> markedStatuses = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private ScanCounts completedCounts;

        FakeScanRepository(List<AssetFileRecord> activeFiles) {
            super(null);
            this.activeFiles = activeFiles;
        }

        @Override
        List<AssetFileRecord> activeFilesForRoot(UUID libraryId, UUID rootId) {
            return activeFiles;
        }

        @Override
        Optional<AssetFileRecord> findActiveFileByPath(UUID libraryId, String normalizedPath) {
            return activeFiles.stream()
                    .filter(file -> file.libraryId().equals(libraryId))
                    .filter(file -> file.normalizedPath().equals(normalizedPath))
                    .findFirst();
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
