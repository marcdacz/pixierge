package com.pixierge.api.scans;

import com.pixierge.api.libraries.LibraryRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class ScanService {

    private final LibraryRepository libraryRepository;
    private final ScanRepository scanRepository;
    private final FileHasher fileHasher;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, Semaphore> libraryScanSlots = new ConcurrentHashMap<>();

    public ScanService(
            LibraryRepository libraryRepository,
            ScanRepository scanRepository,
            FileHasher fileHasher,
            TaskExecutor taskExecutor,
            TransactionTemplate transactionTemplate
    ) {
        this.libraryRepository = libraryRepository;
        this.scanRepository = scanRepository;
        this.fileHasher = fileHasher;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public ScanRunResponse scanLibrary(UUID libraryId, UUID requestedBy) {
        LibraryRepository.LibraryRecord library = findLibrary(libraryId);
        ensureActive(library);
        return startScan(library, null, requestedBy);
    }

    public ScanRunResponse scanRoot(UUID libraryId, UUID rootId, UUID requestedBy) {
        LibraryRepository.LibraryRecord library = findLibrary(libraryId);
        ensureActive(library);
        LibraryRepository.LibraryRootRecord root = library.roots().stream()
                .filter(candidate -> candidate.id().equals(rootId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source path not found"));
        return startScan(library, root, requestedBy);
    }

    @Transactional(readOnly = true)
    public ScanRunResponse getScan(UUID scanRunId) {
        return scanRepository.findScanRun(scanRunId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));
    }

    @Transactional(readOnly = true)
    public List<ScanRunResponse> listLibraryScans(UUID libraryId) {
        findLibrary(libraryId);
        return scanRepository.listScanRuns(libraryId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ScanRunResponse startScan(
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot,
            UUID requestedBy
    ) {
        Semaphore scanSlot = acquireLibraryScanSlot(library.id());
        boolean scanScheduled = false;
        try {
            UUID rootScope = singleRoot == null ? null : singleRoot.id();
            UUID scanRunId = transactionTemplate.execute(status ->
                    scanRepository.createScanRun(library.id(), rootScope, requestedBy, OffsetDateTime.now()));
            taskExecutor.execute(() -> runScan(scanRunId, library, singleRoot, scanSlot));
            scanScheduled = true;
            return readScan(scanRunId);
        } catch (RuntimeException exception) {
            if (!scanScheduled) {
                scanSlot.release();
            }
            throw exception;
        }
    }

    private Semaphore acquireLibraryScanSlot(UUID libraryId) {
        Semaphore scanSlot = libraryScanSlots.computeIfAbsent(libraryId, ignored -> new Semaphore(1));
        try {
            if (!scanSlot.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A scan is already running for this library");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A scan is already running for this library");
        }
        return scanSlot;
    }

    private void runScan(
            UUID scanRunId,
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot,
            Semaphore scanSlot
    ) {
        try {
            executeScan(scanRunId, library, singleRoot);
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                scanRepository.createError(
                        scanRunId,
                        library.id(),
                        singleRoot == null ? null : singleRoot.id(),
                        null,
                        "scan_failed",
                        exception.getMessage() == null ? "Scan failed" : exception.getMessage()
                );
                scanRepository.failScanRun(scanRunId, OffsetDateTime.now());
            });
        } finally {
            scanSlot.release();
        }
    }

    protected void executeScan(
            UUID scanRunId,
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot
    ) {
        ScanCounts counts = new ScanCounts();
        List<LibraryRepository.LibraryRootRecord> roots = singleRoot == null ? library.roots() : List.of(singleRoot);
        List<String> exclusionPatterns = new ArrayList<>(libraryRepository.listGlobalExclusionPatterns().stream()
                .map(LibraryRepository.GlobalExclusionPatternRecord::pattern)
                .toList());
        exclusionPatterns.addAll(library.exclusionPatterns().stream()
                .map(LibraryRepository.LibraryExclusionPatternRecord::pattern)
                .toList());
        ExclusionMatcher exclusionMatcher = new ExclusionMatcher(exclusionPatterns);
        Set<UUID> observedActiveFiles = new HashSet<>();
        Set<UUID> handledPreviousFiles = new HashSet<>();

        for (LibraryRepository.LibraryRootRecord root : roots) {
            Path rootPath = Path.of(root.normalizedPath());
            List<ScanRepository.AssetFileRecord> activeBefore = transactionTemplate.execute(status ->
                    scanRepository.activeFilesForRoot(library.id(), root.id()));
            if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath) || !Files.isExecutable(rootPath)) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), root.normalizedPath(), "root_unavailable", "Source root is unavailable", counts);
                    scanRepository.updateScanRunProgress(scanRunId, counts);
                });
                continue;
            }

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(MediaFileSupport::isSupportedMedia)
                        .filter(path -> !exclusionMatcher.matches(rootPath, path))
                        .forEach(path -> transactionTemplate.executeWithoutResult(status -> {
                            scanFile(scanRunId, library.id(), root, path, counts, observedActiveFiles, handledPreviousFiles);
                            scanRepository.updateScanRunProgress(scanRunId, counts);
                        }));
            } catch (IOException | SecurityException exception) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), root.normalizedPath(), "walk_failed", exception.getMessage(), counts);
                    scanRepository.updateScanRunProgress(scanRunId, counts);
                });
            }

            for (ScanRepository.AssetFileRecord activeFile : activeBefore) {
                if (
                        !observedActiveFiles.contains(activeFile.id())
                                && !handledPreviousFiles.contains(activeFile.id())
                                && !exclusionMatcher.matches(rootPath, Path.of(activeFile.normalizedPath()))
                ) {
                    transactionTemplate.executeWithoutResult(status -> {
                        scanRepository.markStatus(activeFile.id(), "missing", null);
                        scanRepository.createObservation(
                                scanRunId,
                                library.id(),
                                root.id(),
                                activeFile.assetId(),
                                activeFile.id(),
                                activeFile.path(),
                                activeFile.normalizedPath(),
                                activeFile.sizeBytes(),
                                activeFile.modifiedAt(),
                                null,
                                activeFile.contentHash(),
                                "missing"
                        );
                        counts.result("missing");
                        scanRepository.updateScanRunProgress(scanRunId, counts);
                    });
                }
            }
        }

        transactionTemplate.executeWithoutResult(status ->
                scanRepository.completeScanRun(scanRunId, counts, OffsetDateTime.now()));
    }

    private void scanFile(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            Path file,
            ScanCounts counts,
            Set<UUID> observedActiveFiles,
            Set<UUID> handledPreviousFiles
    ) {
        Path normalized = file.toAbsolutePath().normalize();
        try {
            long size = Files.size(normalized);
            OffsetDateTime modifiedAt = OffsetDateTime.ofInstant(
                    Files.getLastModifiedTime(normalized).toInstant(),
                    ZoneOffset.UTC
            );
            FileHasher.Hashes hashes = fileHasher.hash(normalized);
            String normalizedPath = normalized.toString();
            String path = normalizedPath;
            String fileName = normalized.getFileName().toString();

            counts.scanned();
            scanRepository.findActiveFileByPath(libraryId, normalizedPath).ifPresentOrElse(
                    existing -> reconcileExistingPath(
                            scanRunId,
                            root,
                            existing,
                            path,
                            normalizedPath,
                            fileName,
                            size,
                            modifiedAt,
                            hashes,
                            counts,
                            observedActiveFiles,
                            handledPreviousFiles
                    ),
                    () -> reconcileNewPath(
                            scanRunId,
                            libraryId,
                            root,
                            path,
                            normalizedPath,
                            fileName,
                            size,
                            modifiedAt,
                            hashes,
                            counts,
                            observedActiveFiles,
                            handledPreviousFiles
                    )
            );
        } catch (IOException | SecurityException exception) {
            scanRepository.findActiveFileByPath(libraryId, normalized.toString())
                    .ifPresent(activeFile -> handledPreviousFiles.add(activeFile.id()));
            recordError(scanRunId, libraryId, root.id(), normalized.toString(), "file_failed", exception.getMessage(), counts);
        }
    }

    private void reconcileExistingPath(
            UUID scanRunId,
            LibraryRepository.LibraryRootRecord root,
            ScanRepository.AssetFileRecord existing,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            FileHasher.Hashes hashes,
            ScanCounts counts,
            Set<UUID> observedActiveFiles,
            Set<UUID> handledPreviousFiles
    ) {
        if (existing.contentHash().equals(hashes.contentHash())) {
            scanRepository.updateActiveFileSeen(existing.id(), size, modifiedAt, scanRunId, OffsetDateTime.now());
            scanRepository.touchAsset(existing.assetId(), OffsetDateTime.now());
            observedActiveFiles.add(existing.id());
            recordObservation(scanRunId, root, existing.assetId(), existing.id(), path, normalizedPath, size, modifiedAt, hashes, "unchanged", counts);
            return;
        }

        handledPreviousFiles.add(existing.id());
        scanRepository.markStatus(existing.id(), "superseded", null);
        UUID assetId = assetForHash(hashes.contentHash(), MediaFileSupport.mediaType(Path.of(path)));
        UUID assetFileId = scanRepository.createAssetFile(
                assetId,
                existing.libraryId(),
                root.id(),
                path,
                normalizedPath,
                fileName,
                size,
                modifiedAt,
                hashes.contentHash(),
                scanRunId,
                OffsetDateTime.now()
        );
        scanRepository.markStatus(existing.id(), "superseded", assetFileId);
        observedActiveFiles.add(assetFileId);
        recordObservation(scanRunId, root, assetId, assetFileId, path, normalizedPath, size, modifiedAt, hashes, "modified", counts);
    }

    private void reconcileNewPath(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            FileHasher.Hashes hashes,
            ScanCounts counts,
            Set<UUID> observedActiveFiles,
            Set<UUID> handledPreviousFiles
    ) {
        var missingSamePath = scanRepository.findMissingFileByPathAndHash(libraryId, normalizedPath, hashes.contentHash());
        if (missingSamePath.isPresent()) {
            ScanRepository.AssetFileRecord missing = missingSamePath.get();
            scanRepository.reviveMissingFile(missing.id(), size, modifiedAt, scanRunId, OffsetDateTime.now());
            scanRepository.touchAsset(missing.assetId(), OffsetDateTime.now());
            observedActiveFiles.add(missing.id());
            recordObservation(scanRunId, root, missing.assetId(), missing.id(), path, normalizedPath, size, modifiedAt, hashes, "reappeared", counts);
            return;
        }

        var activeWithHash = scanRepository.findActiveFileByHash(libraryId, hashes.contentHash());
        UUID assetId = activeWithHash.map(ScanRepository.AssetFileRecord::assetId)
                .orElseGet(() -> assetForHash(hashes.contentHash(), MediaFileSupport.mediaType(Path.of(path))));
        String result = "added";

        if (activeWithHash.isPresent()) {
            ScanRepository.AssetFileRecord existing = activeWithHash.get();
            if (!Files.exists(Path.of(existing.normalizedPath()))) {
                scanRepository.markStatus(existing.id(), "superseded", null);
                handledPreviousFiles.add(existing.id());
                result = "moved";
            } else {
                result = "duplicate";
            }
        }

        UUID assetFileId = scanRepository.createAssetFile(
                assetId,
                libraryId,
                root.id(),
                path,
                normalizedPath,
                fileName,
                size,
                modifiedAt,
                hashes.contentHash(),
                scanRunId,
                OffsetDateTime.now()
        );
        if (activeWithHash.isPresent() && result.equals("moved")) {
            scanRepository.markStatus(activeWithHash.get().id(), "superseded", assetFileId);
        }
        observedActiveFiles.add(assetFileId);
        recordObservation(scanRunId, root, assetId, assetFileId, path, normalizedPath, size, modifiedAt, hashes, result, counts);
    }

    private UUID assetForHash(String contentHash, String mediaType) {
        return scanRepository.findAssetByHash(contentHash)
                .orElseGet(() -> scanRepository.createAsset(contentHash, mediaType, OffsetDateTime.now()));
    }

    private void recordObservation(
            UUID scanRunId,
            LibraryRepository.LibraryRootRecord root,
            UUID assetId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            long size,
            OffsetDateTime modifiedAt,
            FileHasher.Hashes hashes,
            String result,
            ScanCounts counts
    ) {
        scanRepository.createObservation(
                scanRunId,
                root.libraryId(),
                root.id(),
                assetId,
                assetFileId,
                path,
                normalizedPath,
                size,
                modifiedAt,
                hashes.partialHash(),
                hashes.contentHash(),
                result
        );
        counts.result(result);
    }

    private void recordError(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            String path,
            String errorCode,
            String message,
            ScanCounts counts
    ) {
        scanRepository.createError(scanRunId, libraryId, rootId, path, errorCode, message == null ? errorCode : message);
        counts.result("error");
    }

    private ScanRunResponse toResponse(ScanRepository.ScanRunRecord run) {
        return new ScanRunResponse(
                run.id(),
                run.libraryId(),
                run.rootId(),
                run.status(),
                run.startedAt(),
                run.completedAt(),
                run.scannedFileCount(),
                run.addedCount(),
                run.unchangedCount(),
                run.movedCount(),
                run.modifiedCount(),
                run.duplicateCount(),
                run.missingCount(),
                run.reappearedCount(),
                run.errorCount(),
                scanRepository.errorsForScan(run.id()).stream()
                        .map(error -> new ScanErrorResponse(
                                error.id(),
                                error.path(),
                                error.errorCode(),
                                error.message(),
                                error.createdAt()
                        ))
                        .toList()
        );
    }

    private LibraryRepository.LibraryRecord findLibrary(UUID libraryId) {
        return libraryRepository.findLibrary(libraryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library not found"));
    }

    private ScanRunResponse readScan(UUID scanRunId) {
        return transactionTemplate.execute(status -> scanRepository.findScanRun(scanRunId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found")));
    }

    private void ensureActive(LibraryRepository.LibraryRecord library) {
        if (!"active".equals(library.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived libraries must be restored before scanning");
        }
    }
}
