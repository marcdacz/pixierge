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

    private static final int OBSERVATION_BATCH_SIZE = 100;

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

    @Transactional(readOnly = true)
    public List<ActiveScanResponse> listActiveScans() {
        return scanRepository.listActiveScanRuns().stream()
                .map(this::toActiveResponse)
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
        ScanProgressThrottle progress = new ScanProgressThrottle(scanRunId, scanRepository, counts);
        List<LibraryRepository.LibraryRootRecord> roots = singleRoot == null ? library.roots() : List.of(singleRoot);
        List<String> exclusionPatterns = new ArrayList<>(libraryRepository.listGlobalExclusionPatterns().stream()
                .map(LibraryRepository.GlobalExclusionPatternRecord::pattern)
                .toList());
        exclusionPatterns.addAll(library.exclusionPatterns().stream()
                .map(LibraryRepository.LibraryExclusionPatternRecord::pattern)
                .toList());
        ExclusionMatcher exclusionMatcher = new ExclusionMatcher(exclusionPatterns);
        Set<UUID> handledPreviousFiles = new HashSet<>();
        List<HashWorkItem> hashQueue = new ArrayList<>();
        List<ScanRepository.ObservationInsert> pendingObservations = new ArrayList<>();

        for (LibraryRepository.LibraryRootRecord root : roots) {
            Path rootPath = Path.of(root.normalizedPath());
            if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath) || !Files.isExecutable(rootPath)) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), root.normalizedPath(), "root_unavailable", "Source root is unavailable", counts);
                    flushProgress(progress);
                });
                continue;
            }

            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(MediaFileSupport::isSupportedMedia)
                        .filter(path -> !exclusionMatcher.matches(rootPath, path))
                        .forEach(path -> catalogFile(
                                scanRunId,
                                library.id(),
                                root,
                                path,
                                counts,
                                hashQueue,
                                pendingObservations,
                                handledPreviousFiles,
                                progress
                        ));
            } catch (IOException | SecurityException exception) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), root.normalizedPath(), "walk_failed", exception.getMessage(), counts);
                    flushProgress(progress);
                });
            }

            flushObservations(pendingObservations);
            flushProgress(progress);

            for (HashWorkItem item : hashQueue) {
                if (!item.rootId().equals(root.id())) {
                    continue;
                }
                transactionTemplate.executeWithoutResult(status ->
                        hashAndReconcile(scanRunId, root, item, counts, handledPreviousFiles, pendingObservations));
                maybeFlushProgress(progress);
                if (pendingObservations.size() >= OBSERVATION_BATCH_SIZE) {
                    flushObservations(pendingObservations);
                }
            }
            hashQueue.removeIf(item -> item.rootId().equals(root.id()));

            List<ScanRepository.AssetFileRecord> notSeen = transactionTemplate.execute(status ->
                    scanRepository.activeFilesNotSeenInRun(library.id(), root.id(), scanRunId));
            for (ScanRepository.AssetFileRecord activeFile : notSeen) {
                if (
                        handledPreviousFiles.contains(activeFile.id())
                                || exclusionMatcher.matches(rootPath, Path.of(activeFile.normalizedPath()))
                ) {
                    continue;
                }
                transactionTemplate.executeWithoutResult(status -> {
                    scanRepository.markStatus(activeFile.id(), "missing", null);
                    pendingObservations.add(observation(
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
                    ));
                    counts.result("missing");
                    progress.maybeUpdate();
                });
            }
        }

        flushObservations(pendingObservations);
        flushProgress(progress);
        transactionTemplate.executeWithoutResult(status ->
                scanRepository.completeScanRun(scanRunId, counts, OffsetDateTime.now()));
    }

    private void catalogFile(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            Path file,
            ScanCounts counts,
            List<HashWorkItem> hashQueue,
            List<ScanRepository.ObservationInsert> pendingObservations,
            Set<UUID> handledPreviousFiles,
            ScanProgressThrottle progress
    ) {
        Path normalized = file.toAbsolutePath().normalize();
        try {
            long size = Files.size(normalized);
            OffsetDateTime modifiedAt = OffsetDateTime.ofInstant(
                    Files.getLastModifiedTime(normalized).toInstant(),
                    ZoneOffset.UTC
            );
            String normalizedPath = normalized.toString();
            String path = normalizedPath;
            String fileName = normalized.getFileName().toString();
            OffsetDateTime now = OffsetDateTime.now();

            transactionTemplate.executeWithoutResult(status -> {
                counts.scanned();
                scanRepository.findActiveFileByPath(libraryId, normalizedPath).ifPresentOrElse(
                        existing -> catalogExistingPath(
                                scanRunId,
                                root,
                                existing,
                                path,
                                normalizedPath,
                                fileName,
                                size,
                                modifiedAt,
                                now,
                                counts,
                                hashQueue,
                                pendingObservations
                        ),
                        () -> catalogNewPath(
                                scanRunId,
                                libraryId,
                                root,
                                path,
                                normalizedPath,
                                fileName,
                                size,
                                modifiedAt,
                                now,
                                counts,
                                hashQueue
                        )
                );
                progress.maybeUpdate();
            });
        } catch (IOException | SecurityException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                scanRepository.findActiveFileByPath(libraryId, normalized.toString())
                        .ifPresent(activeFile -> handledPreviousFiles.add(activeFile.id()));
                recordError(scanRunId, libraryId, root.id(), normalized.toString(), "file_failed", exception.getMessage(), counts);
                progress.maybeUpdate();
            });
        }
    }

    private void catalogExistingPath(
            UUID scanRunId,
            LibraryRepository.LibraryRootRecord root,
            ScanRepository.AssetFileRecord existing,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            OffsetDateTime now,
            ScanCounts counts,
            List<HashWorkItem> hashQueue,
            List<ScanRepository.ObservationInsert> pendingObservations
    ) {
        scanRepository.updateActiveFileSeen(existing.id(), size, modifiedAt, scanRunId, now);
        scanRepository.touchAsset(existing.assetId(), now);

        if (ProvisionalIdentity.pathUnchanged(existing, size, modifiedAt)
                && !ProvisionalIdentity.isProvisional(existing.contentHash())) {
            pendingObservations.add(observation(
                    scanRunId,
                    root.libraryId(),
                    root.id(),
                    existing.assetId(),
                    existing.id(),
                    path,
                    normalizedPath,
                    size,
                    modifiedAt,
                    null,
                    existing.contentHash(),
                    "unchanged"
            ));
            counts.result("unchanged");
            return;
        }

        hashQueue.add(new HashWorkItem(
                root.id(),
                existing.id(),
                path,
                normalizedPath,
                fileName,
                size,
                modifiedAt,
                existing
        ));
    }

    private void catalogNewPath(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            OffsetDateTime now,
            ScanCounts counts,
            List<HashWorkItem> hashQueue
    ) {
        String provisionalHash = ProvisionalIdentity.fingerprint(normalizedPath, size, modifiedAt);
        UUID assetId = assetForHash(provisionalHash, MediaFileSupport.mediaType(Path.of(path)));
        UUID assetFileId = scanRepository.createAssetFile(
                assetId,
                libraryId,
                root.id(),
                path,
                normalizedPath,
                fileName,
                size,
                modifiedAt,
                provisionalHash,
                scanRunId,
                now
        );
        counts.result("added");
        hashQueue.add(new HashWorkItem(
                root.id(),
                assetFileId,
                path,
                normalizedPath,
                fileName,
                size,
                modifiedAt,
                null
        ));
    }

    private void hashAndReconcile(
            UUID scanRunId,
            LibraryRepository.LibraryRootRecord root,
            HashWorkItem item,
            ScanCounts counts,
            Set<UUID> handledPreviousFiles,
            List<ScanRepository.ObservationInsert> pendingObservations
    ) {
        Path normalized = Path.of(item.normalizedPath());
        try {
            FileHasher.Hashes hashes = fileHasher.hash(normalized);
            OffsetDateTime now = OffsetDateTime.now();
            ScanRepository.AssetFileRecord existing = item.existingFile() == null
                    ? scanRepository.findActiveFileByPath(root.libraryId(), item.normalizedPath()).orElse(null)
                    : item.existingFile();

            if (existing == null) {
                return;
            }

            if (ProvisionalIdentity.isProvisional(existing.contentHash())) {
                promoteProvisionalFile(
                        scanRunId,
                        root,
                        existing,
                        item.path(),
                        item.normalizedPath(),
                        item.fileName(),
                        item.size(),
                        item.modifiedAt(),
                        hashes,
                        counts,
                        handledPreviousFiles,
                        pendingObservations,
                        now
                );
                return;
            }

            if (existing.contentHash().equals(hashes.contentHash())) {
                pendingObservations.add(observation(
                        scanRunId,
                        root.libraryId(),
                        root.id(),
                        existing.assetId(),
                        existing.id(),
                        item.path(),
                        item.normalizedPath(),
                        item.size(),
                        item.modifiedAt(),
                        hashes.partialHash(),
                        hashes.contentHash(),
                        "unchanged"
                ));
                counts.result("unchanged");
                return;
            }

            reconcileModifiedPath(
                    scanRunId,
                    root,
                    existing,
                    item.path(),
                    item.normalizedPath(),
                    item.fileName(),
                    item.size(),
                    item.modifiedAt(),
                    hashes,
                    counts,
                    handledPreviousFiles,
                    pendingObservations,
                    now
            );
        } catch (IOException | SecurityException exception) {
            if (item.existingFile() != null) {
                handledPreviousFiles.add(item.existingFile().id());
            }
            recordError(scanRunId, root.libraryId(), root.id(), item.normalizedPath(), "file_failed", exception.getMessage(), counts);
        }
    }

    private void promoteProvisionalFile(
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
            Set<UUID> handledPreviousFiles,
            List<ScanRepository.ObservationInsert> pendingObservations,
            OffsetDateTime now
    ) {
        var missingSamePath = scanRepository.findMissingFileByPathAndHash(root.libraryId(), normalizedPath, hashes.contentHash());
        if (missingSamePath.isPresent()) {
            ScanRepository.AssetFileRecord missing = missingSamePath.get();
            scanRepository.markStatus(existing.id(), "superseded", missing.id());
            scanRepository.reviveMissingFile(missing.id(), size, modifiedAt, scanRunId, now);
            scanRepository.touchAsset(missing.assetId(), now);
            pendingObservations.add(observation(
                    scanRunId,
                    root.libraryId(),
                    root.id(),
                    missing.assetId(),
                    missing.id(),
                    path,
                    normalizedPath,
                    size,
                    modifiedAt,
                    hashes.partialHash(),
                    hashes.contentHash(),
                    "reappeared"
            ));
            counts.result("reappeared");
            return;
        }

        var activeWithHash = scanRepository.findActiveFileByHash(root.libraryId(), hashes.contentHash());
        if (activeWithHash.isPresent() && !activeWithHash.get().id().equals(existing.id())) {
            ScanRepository.AssetFileRecord duplicateSource = activeWithHash.get();
            handledPreviousFiles.add(existing.id());
            scanRepository.markStatus(existing.id(), "superseded", null);
            String result = "duplicate";
            if (!Files.exists(Path.of(duplicateSource.normalizedPath()))) {
                scanRepository.markStatus(duplicateSource.id(), "superseded", null);
                handledPreviousFiles.add(duplicateSource.id());
                result = "moved";
            }
            UUID assetFileId = scanRepository.createAssetFile(
                    duplicateSource.assetId(),
                    existing.libraryId(),
                    root.id(),
                    path,
                    normalizedPath,
                    fileName,
                    size,
                    modifiedAt,
                    hashes.contentHash(),
                    scanRunId,
                    now
            );
            if (result.equals("moved")) {
                scanRepository.markStatus(duplicateSource.id(), "superseded", assetFileId);
            }
            pendingObservations.add(observation(
                    scanRunId,
                    root.libraryId(),
                    root.id(),
                    duplicateSource.assetId(),
                    assetFileId,
                    path,
                    normalizedPath,
                    size,
                    modifiedAt,
                    hashes.partialHash(),
                    hashes.contentHash(),
                    result
            ));
            counts.result(result);
            return;
        }

        scanRepository.updateAssetContentHash(existing.assetId(), hashes.contentHash(), now);
        scanRepository.updateAssetFileContentHash(existing.id(), hashes.contentHash());
        scanRepository.updateActiveFileSeen(existing.id(), size, modifiedAt, scanRunId, now);
        scanRepository.touchAsset(existing.assetId(), now);
        pendingObservations.add(observation(
                scanRunId,
                root.libraryId(),
                root.id(),
                existing.assetId(),
                existing.id(),
                path,
                normalizedPath,
                size,
                modifiedAt,
                hashes.partialHash(),
                hashes.contentHash(),
                "added"
        ));
    }

    private void reconcileModifiedPath(
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
            Set<UUID> handledPreviousFiles,
            List<ScanRepository.ObservationInsert> pendingObservations,
            OffsetDateTime now
    ) {
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
                now
        );
        scanRepository.markStatus(existing.id(), "superseded", assetFileId);
        pendingObservations.add(observation(
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
                "modified"
        ));
        counts.result("modified");
    }

    private UUID assetForHash(String contentHash, String mediaType) {
        return scanRepository.findAssetByHash(contentHash)
                .orElseGet(() -> scanRepository.createAsset(contentHash, mediaType, OffsetDateTime.now()));
    }

    private void maybeFlushProgress(ScanProgressThrottle progress) {
        if (progress.shouldUpdate()) {
            flushProgress(progress);
        }
    }

    private void flushProgress(ScanProgressThrottle progress) {
        transactionTemplate.executeWithoutResult(status -> progress.flush());
    }

    private void flushObservations(List<ScanRepository.ObservationInsert> pendingObservations) {
        if (pendingObservations.isEmpty()) {
            return;
        }
        List<ScanRepository.ObservationInsert> batch = new ArrayList<>(pendingObservations);
        pendingObservations.clear();
        transactionTemplate.executeWithoutResult(status -> scanRepository.createObservations(batch));
    }

    private ScanRepository.ObservationInsert observation(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            UUID assetId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            long sizeBytes,
            OffsetDateTime modifiedAt,
            String partialHash,
            String contentHash,
            String result
    ) {
        return new ScanRepository.ObservationInsert(
                scanRunId,
                libraryId,
                rootId,
                assetId,
                assetFileId,
                path,
                normalizedPath,
                sizeBytes,
                modifiedAt,
                partialHash,
                contentHash,
                result
        );
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

    private ActiveScanResponse toActiveResponse(ScanRepository.ActiveScanRecord run) {
        return new ActiveScanResponse(
                run.id(),
                run.libraryId(),
                run.libraryName(),
                run.rootId(),
                run.rootPath(),
                run.status(),
                run.startedAt(),
                run.scannedFileCount(),
                run.addedCount(),
                run.unchangedCount(),
                run.movedCount(),
                run.modifiedCount(),
                run.duplicateCount(),
                run.missingCount(),
                run.reappearedCount(),
                run.errorCount()
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

    private record HashWorkItem(
            UUID rootId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt,
            ScanRepository.AssetFileRecord existingFile
    ) {
    }
}
