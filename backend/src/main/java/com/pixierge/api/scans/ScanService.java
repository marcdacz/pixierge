package com.pixierge.api.scans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.libraries.LibraryRepository;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ScanService {

    private static final int OBSERVATION_BATCH_SIZE = 100;
    private static final int CATALOG_BATCH_SIZE = 500;
    private static final int DEFAULT_IDENTITY_BATCH_SIZE = 100;

    private final LibraryRepository libraryRepository;
    private final ScanRepository scanRepository;
    private final FileHasher fileHasher;
    private final BackgroundJobService backgroundJobService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final int identityBatchSize;
    private final Map<UUID, Semaphore> libraryScanSlots = new ConcurrentHashMap<>();

    public ScanService(
            LibraryRepository libraryRepository,
            ScanRepository scanRepository,
            FileHasher fileHasher,
            BackgroundJobService backgroundJobService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            @Value("${pixierge.background-jobs.identity-batch-size:" + DEFAULT_IDENTITY_BATCH_SIZE + "}") int identityBatchSize
    ) {
        this.libraryRepository = libraryRepository;
        this.scanRepository = scanRepository;
        this.fileHasher = fileHasher;
        this.backgroundJobService = backgroundJobService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.identityBatchSize = Math.max(1, identityBatchSize);
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
            UUID scanRunId = transactionTemplate.execute(status -> {
                if (scanRepository.hasActiveScanRun(library.id())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A scan is already running for this library");
                }
                OffsetDateTime now = OffsetDateTime.now();
                UUID createdScanRunId = scanRepository.createScanRun(library.id(), rootScope, requestedBy, now);
                backgroundJobService.enqueue(catalogJob(createdScanRunId, library.id(), rootScope, now));
                return createdScanRunId;
            });
            scanScheduled = true;
            return readScan(scanRunId);
        } catch (RuntimeException exception) {
            if (!scanScheduled) {
                scanSlot.release();
            }
            throw exception;
        } finally {
            if (scanScheduled) {
                scanSlot.release();
            }
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

    private BackgroundJobCreate catalogJob(UUID scanRunId, UUID libraryId, UUID rootId, OffsetDateTime now) {
        return catalogJob(scanRunId, libraryId, rootId, null, now);
    }

    private BackgroundJobCreate catalogJob(
            UUID scanRunId,
            UUID libraryId,
            UUID rootId,
            String subtreePath,
            OffsetDateTime now
    ) {
        try {
            String payload = objectMapper.writeValueAsString(new ScanCatalogJobPayload(
                    scanRunId,
                    libraryId,
                    rootId,
                    subtreePath
            ));
            String jobType = subtreePath == null ? ScanJobTypes.LIBRARY_CATALOG_ROOT : ScanJobTypes.LIBRARY_CATALOG_SUBTREE;
            return new BackgroundJobCreate(
                    jobType,
                    payload,
                    0,
                    1,
                    now,
                    "library:" + libraryId,
                    jobType + ":" + scanRunId
            );
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create scan job", exception);
        }
    }

    public UUID enqueueFilesystemChangeScan(UUID libraryId, UUID rootId, String changedPath) {
        LibraryRepository.LibraryRecord library = libraryRepository.findLibrary(libraryId).orElse(null);
        if (library == null || !"active".equals(library.status())) {
            return null;
        }
        LibraryRepository.LibraryRootRecord root = library.roots().stream()
                .filter(candidate -> candidate.id().equals(rootId))
                .findFirst()
                .orElse(null);
        if (root == null) {
            return null;
        }
        Path rootPath = Path.of(root.normalizedPath());
        Path subtree = normalizeSubtree(rootPath, changedPath);
        if (subtree == null) {
            return null;
        }

        return transactionTemplate.execute(status -> {
            if (scanRepository.hasActiveScanRun(library.id())) {
                return null;
            }
            OffsetDateTime now = OffsetDateTime.now();
            UUID scanRunId = scanRepository.createScanRun(library.id(), root.id(), null, now);
            backgroundJobService.enqueue(catalogJob(
                    scanRunId,
                    library.id(),
                    root.id(),
                    subtree.toString(),
                    now
            ));
            return scanRunId;
        });
    }

    private Path normalizeSubtree(Path rootPath, String changedPath) {
        if (changedPath == null || changedPath.isBlank()) {
            return rootPath;
        }
        Path normalized = Path.of(changedPath).toAbsolutePath().normalize();
        if (!normalized.startsWith(rootPath)) {
            return null;
        }
        if (Files.isRegularFile(normalized)) {
            Path parent = normalized.getParent();
            return parent == null ? rootPath : parent;
        }
        return normalized;
    }

    void executeCatalogJob(ScanCatalogJobPayload payload, UUID jobId) {
        try {
            LibraryRepository.LibraryRecord library = findLibrary(payload.libraryId());
            ensureActive(library);
            LibraryRepository.LibraryRootRecord root = payload.rootId() == null
                    ? null
                    : library.roots().stream()
                    .filter(candidate -> candidate.id().equals(payload.rootId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source path not found"));
            transactionTemplate.executeWithoutResult(status ->
                    scanRepository.markScanRunRunning(payload.scanRunId(), OffsetDateTime.now())
            );
            executeScan(payload.scanRunId(), library, root, payload.subtreePath(), jobId);
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                scanRepository.createError(
                        payload.scanRunId(),
                        payload.libraryId(),
                        payload.rootId(),
                        null,
                        "scan_failed",
                        exception.getMessage() == null ? "Scan failed" : exception.getMessage()
                );
                scanRepository.failScanRun(payload.scanRunId(), OffsetDateTime.now());
            });
            throw exception;
        }
    }

    protected void executeScan(
            UUID scanRunId,
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot
    ) {
        executeScan(scanRunId, library, singleRoot, null, null);
    }

    private void executeScan(
            UUID scanRunId,
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot,
            String subtreePath,
            UUID currentCatalogJobId
    ) {
        ScanCounts counts = new ScanCounts();
        ScanProgressThrottle progress = new ScanProgressThrottle(scanRunId, scanRepository, counts);
        List<LibraryRepository.LibraryRootRecord> roots = singleRoot == null ? library.roots() : List.of(singleRoot);
        ExclusionMatcher exclusionMatcher = exclusionMatcher(library);
        List<HashWorkItem> hashQueue = new ArrayList<>();
        List<ScanRepository.ObservationInsert> pendingObservations = new ArrayList<>();

        for (LibraryRepository.LibraryRootRecord root : roots) {
            Path rootPath = Path.of(root.normalizedPath());
            Path scanPath = catalogStartPath(rootPath, subtreePath);
            if (!Files.isDirectory(scanPath) || !Files.isReadable(scanPath) || !Files.isExecutable(scanPath)) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), scanPath.toString(), "root_unavailable", "Source root is unavailable", counts);
                    flushProgress(progress);
                });
                continue;
            }

            List<FileCandidate> catalogBatch = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(scanPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(MediaFileSupport::isSupportedMedia)
                        .filter(path -> !exclusionMatcher.matches(rootPath, path))
                        .forEach(path -> catalogFileCandidate(
                                scanRunId,
                                library.id(),
                                root,
                                path,
                                counts,
                                catalogBatch,
                                hashQueue,
                                pendingObservations,
                                progress
                        ));
            } catch (IOException | SecurityException exception) {
                transactionTemplate.executeWithoutResult(status -> {
                    recordError(scanRunId, library.id(), root.id(), root.normalizedPath(), "walk_failed", exception.getMessage(), counts);
                    flushProgress(progress);
                });
            }
            catalogFileBatch(scanRunId, library.id(), root, catalogBatch, counts, hashQueue, pendingObservations, progress);

            flushObservations(pendingObservations);
            flushProgress(progress);
        }

        enqueueIdentityJobs(scanRunId, library.id(), hashQueue, subtreePath, OffsetDateTime.now());
        transactionTemplate.executeWithoutResult(status ->
                scanRepository.markScanCatalogCompleted(scanRunId, OffsetDateTime.now()));
        flushObservations(pendingObservations);
        flushProgress(progress);
        if (currentCatalogJobId == null) {
            tryCompleteScanIfReady(scanRunId, library, roots, exclusionMatcher, subtreePath, null);
        }
    }

    private Path catalogStartPath(Path rootPath, String subtreePath) {
        if (subtreePath == null || subtreePath.isBlank()) {
            return rootPath;
        }
        Path scanPath = Path.of(subtreePath).toAbsolutePath().normalize();
        return scanPath.startsWith(rootPath) ? scanPath : rootPath;
    }

    private void catalogFileCandidate(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            Path file,
            ScanCounts counts,
            List<FileCandidate> catalogBatch,
            List<HashWorkItem> hashQueue,
            List<ScanRepository.ObservationInsert> pendingObservations,
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
            catalogBatch.add(new FileCandidate(path, normalizedPath, fileName, size, modifiedAt));
            if (catalogBatch.size() >= CATALOG_BATCH_SIZE) {
                catalogFileBatch(scanRunId, libraryId, root, catalogBatch, counts, hashQueue, pendingObservations, progress);
            }
        } catch (IOException | SecurityException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                recordError(scanRunId, libraryId, root.id(), normalized.toString(), "file_failed", exception.getMessage(), counts);
                progress.maybeUpdate();
            });
        }
    }

    private void catalogFileBatch(
            UUID scanRunId,
            UUID libraryId,
            LibraryRepository.LibraryRootRecord root,
            List<FileCandidate> catalogBatch,
            ScanCounts counts,
            List<HashWorkItem> hashQueue,
            List<ScanRepository.ObservationInsert> pendingObservations,
            ScanProgressThrottle progress
    ) {
        if (catalogBatch.isEmpty()) {
            return;
        }
        List<FileCandidate> candidates = List.copyOf(catalogBatch);
        catalogBatch.clear();
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            Map<String, ScanRepository.AssetFileRecord> existingByPath = scanRepository.findActiveFilesByPaths(
                    libraryId,
                    candidates.stream().map(FileCandidate::normalizedPath).toList()
            );
            for (FileCandidate candidate : candidates) {
                counts.scanned();
                ScanRepository.AssetFileRecord existing = existingByPath.get(candidate.normalizedPath());
                if (existing == null) {
                    catalogNewPath(
                            scanRunId,
                            libraryId,
                            root,
                            candidate.path(),
                            candidate.normalizedPath(),
                            candidate.fileName(),
                            candidate.size(),
                            candidate.modifiedAt(),
                            now,
                            counts,
                            hashQueue
                    );
                } else {
                    catalogExistingPath(
                            scanRunId,
                            root,
                            existing,
                            candidate.path(),
                            candidate.normalizedPath(),
                            candidate.fileName(),
                            candidate.size(),
                            candidate.modifiedAt(),
                            now,
                            counts,
                            hashQueue,
                            pendingObservations
                    );
                }
                progress.maybeUpdate();
            }
        });
        if (pendingObservations.size() >= OBSERVATION_BATCH_SIZE) {
            flushObservations(pendingObservations);
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

    private void enqueueIdentityJobs(
            UUID scanRunId,
            UUID libraryId,
            List<HashWorkItem> hashQueue,
            String subtreePath,
            OffsetDateTime now
    ) {
        for (int start = 0, batchIndex = 0; start < hashQueue.size(); start += identityBatchSize, batchIndex++) {
            int end = Math.min(start + identityBatchSize, hashQueue.size());
            backgroundJobService.enqueue(identityJob(
                    scanRunId,
                    libraryId,
                    batchIndex,
                    hashQueue.subList(start, end),
                    subtreePath,
                    now
            ));
        }
    }

    private BackgroundJobCreate identityJob(
            UUID scanRunId,
            UUID libraryId,
            int batchIndex,
            List<HashWorkItem> items,
            String subtreePath,
            OffsetDateTime now
    ) {
        try {
            UUID rootId = sharedRootId(items);
            ScanIdentityJobPayload payload = new ScanIdentityJobPayload(
                    scanRunId,
                    libraryId,
                    rootId,
                    items.stream()
                            .map(item -> new ScanIdentityJobPayload.ScanIdentityJobItem(
                                    item.rootId(),
                                    item.assetFileId(),
                                    item.path(),
                                    item.normalizedPath(),
                                    item.fileName(),
                                    item.size(),
                                    item.modifiedAt()
                            ))
                            .toList(),
                    subtreePath
            );
            String payloadJson = objectMapper.writeValueAsString(payload);
            return new BackgroundJobCreate(
                    ScanJobTypes.ASSET_IDENTITY_BACKFILL,
                    payloadJson,
                    0,
                    3,
                    now,
                    ScanJobTypes.ASSET_IDENTITY_BACKFILL + ":" + scanRunId + ":batch:" + batchIndex,
                    identityDedupeKey(scanRunId, batchIndex)
            );
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create identity job", exception);
        }
    }

    private UUID sharedRootId(List<HashWorkItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        UUID rootId = items.getFirst().rootId();
        return items.stream().allMatch(item -> item.rootId().equals(rootId)) ? rootId : null;
    }

    private String identityDedupeKey(UUID scanRunId, int batchIndex) {
        return ScanJobTypes.ASSET_IDENTITY_BACKFILL + ":" + scanRunId + ":batch:" + batchIndex;
    }

    void executeIdentityJob(ScanIdentityJobPayload payload, UUID jobId) {
        LibraryRepository.LibraryRecord library = libraryRepository.findLibrary(payload.libraryId()).orElse(null);
        if (library == null) {
            return;
        }
        ensureActive(library);
        Map<UUID, LibraryRepository.LibraryRootRecord> rootsById = library.roots().stream()
                .collect(Collectors.toMap(LibraryRepository.LibraryRootRecord::id, root -> root));

        List<IdentityHashResult> results = new ArrayList<>();
        for (ScanIdentityJobPayload.ScanIdentityJobItem item : payload.identityItems()) {
            if (!rootsById.containsKey(item.rootId())) {
                continue;
            }
            ScanRepository.AssetFileRecord candidate = transactionTemplate.execute(status ->
                    scanRepository.findAssetFile(item.assetFileId()).orElse(null));
            if (candidate == null || !identityPayloadMatches(payload, item, candidate)) {
                continue;
            }
            try {
                results.add(new IdentityHashResult(item, fileHasher.hash(Path.of(item.normalizedPath())), null));
            } catch (IOException | SecurityException exception) {
                results.add(new IdentityHashResult(item, null, exception.getMessage()));
            }
        }
        if (results.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            ScanCounts counts = new ScanCounts();
            List<ScanRepository.ObservationInsert> pendingObservations = new ArrayList<>();
            for (IdentityHashResult result : results) {
                ScanIdentityJobPayload.ScanIdentityJobItem item = result.item();
                LibraryRepository.LibraryRootRecord root = rootsById.get(item.rootId());
                if (root == null) {
                    continue;
                }
                ScanRepository.AssetFileRecord current = scanRepository.findAssetFile(item.assetFileId()).orElse(null);
                if (current == null || !identityPayloadMatches(payload, item, current)) {
                    continue;
                }
                if (result.errorMessage() != null) {
                    recordError(
                            payload.scanRunId(),
                            payload.libraryId(),
                            item.rootId(),
                            item.normalizedPath(),
                            "file_failed",
                            result.errorMessage(),
                            counts
                    );
                    continue;
                }
                reconcileHashes(
                        payload.scanRunId(),
                        root,
                        new HashWorkItem(
                                item.rootId(),
                                item.assetFileId(),
                                item.path(),
                                item.normalizedPath(),
                                item.fileName(),
                                item.size(),
                                item.modifiedAt(),
                                current
                        ),
                        result.hashes(),
                        counts,
                        pendingObservations
                );
                if (pendingObservations.size() >= OBSERVATION_BATCH_SIZE) {
                    scanRepository.createObservations(pendingObservations);
                    pendingObservations.clear();
                }
            }
            scanRepository.createObservations(pendingObservations);
            scanRepository.incrementScanRunCounts(payload.scanRunId(), counts);
        });
    }

    private boolean identityPayloadMatches(
            ScanIdentityJobPayload payload,
            ScanIdentityJobPayload.ScanIdentityJobItem item,
            ScanRepository.AssetFileRecord current
    ) {
        return "active".equals(current.status())
                && current.libraryId().equals(payload.libraryId())
                && current.rootId().equals(item.rootId())
                && current.normalizedPath().equals(item.normalizedPath())
                && current.sizeBytes() == item.size()
                && current.modifiedAt().toInstant().toEpochMilli() == item.modifiedAt().toInstant().toEpochMilli();
    }

    private record IdentityHashResult(
            ScanIdentityJobPayload.ScanIdentityJobItem item,
            FileHasher.Hashes hashes,
            String errorMessage
    ) {
    }

    private void reconcileHashes(
            UUID scanRunId,
            LibraryRepository.LibraryRootRecord root,
            HashWorkItem item,
            FileHasher.Hashes hashes,
            ScanCounts counts,
            List<ScanRepository.ObservationInsert> pendingObservations
    ) {
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
                pendingObservations,
                now
        );
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
            scanRepository.markStatus(existing.id(), "superseded", null);
            String result = "duplicate";
            if (!Files.exists(Path.of(duplicateSource.normalizedPath()))) {
                scanRepository.markStatus(duplicateSource.id(), "superseded", null);
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

        var existingAssetWithHash = scanRepository.findAssetByHash(hashes.contentHash());
        if (existingAssetWithHash.isPresent() && !existingAssetWithHash.get().equals(existing.assetId())) {
            scanRepository.markStatus(existing.id(), "superseded", null);
            UUID assetFileId = scanRepository.createAssetFile(
                    existingAssetWithHash.get(),
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
                    existingAssetWithHash.get(),
                    assetFileId,
                    path,
                    normalizedPath,
                    size,
                    modifiedAt,
                    hashes.partialHash(),
                    hashes.contentHash(),
                    "added"
            ));
            counts.result("added");
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
        counts.result("added");
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
            List<ScanRepository.ObservationInsert> pendingObservations,
            OffsetDateTime now
    ) {
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

    void tryCompleteCatalogScan(ScanCatalogJobPayload payload) {
        LibraryRepository.LibraryRecord library = libraryRepository.findLibrary(payload.libraryId()).orElse(null);
        if (library == null) {
            return;
        }
        LibraryRepository.LibraryRootRecord root = payload.rootId() == null
                ? null
                : library.roots().stream()
                .filter(candidate -> candidate.id().equals(payload.rootId()))
                .findFirst()
                .orElse(null);
        tryCompleteScanIfReady(
                payload.scanRunId(),
                library,
                rootsForScan(library, root),
                exclusionMatcher(library),
                payload.subtreePath(),
                null
        );
    }

    void tryCompleteIdentityScan(ScanIdentityJobPayload payload) {
        LibraryRepository.LibraryRecord library = libraryRepository.findLibrary(payload.libraryId()).orElse(null);
        if (library == null) {
            return;
        }
        LibraryRepository.LibraryRootRecord root = library.roots().stream()
                .filter(candidate -> candidate.id().equals(payload.completionRootId()))
                .findFirst()
                .orElse(null);
        tryCompleteScanIfReady(
                payload.scanRunId(),
                library,
                rootsForScan(library, root),
                exclusionMatcher(library),
                payload.subtreePath(),
                null
        );
    }

    private List<LibraryRepository.LibraryRootRecord> rootsForScan(
            LibraryRepository.LibraryRecord library,
            LibraryRepository.LibraryRootRecord singleRoot
    ) {
        return singleRoot == null ? library.roots() : List.of(singleRoot);
    }

    private ExclusionMatcher exclusionMatcher(LibraryRepository.LibraryRecord library) {
        List<String> exclusionPatterns = new ArrayList<>(libraryRepository.listGlobalExclusionPatterns().stream()
                .map(LibraryRepository.GlobalExclusionPatternRecord::pattern)
                .toList());
        exclusionPatterns.addAll(library.exclusionPatterns().stream()
                .map(LibraryRepository.LibraryExclusionPatternRecord::pattern)
                .toList());
        return new ExclusionMatcher(exclusionPatterns);
    }

    private void tryCompleteScanIfReady(
            UUID scanRunId,
            LibraryRepository.LibraryRecord library,
            List<LibraryRepository.LibraryRootRecord> roots,
            ExclusionMatcher exclusionMatcher,
            String subtreePath,
            UUID currentJobId
    ) {
        boolean catalogCompleted = transactionTemplate.execute(status -> scanRepository.isScanCatalogCompleted(scanRunId));
        if (!catalogCompleted) {
            return;
        }
        if (backgroundJobService.hasActiveJobs(
                ScanJobTypes.LIBRARY_CATALOG_ROOT,
                ScanJobTypes.LIBRARY_CATALOG_ROOT + ":" + scanRunId,
                currentJobId
        )) {
            return;
        }
        if (backgroundJobService.hasActiveJobs(
                ScanJobTypes.LIBRARY_CATALOG_SUBTREE,
                ScanJobTypes.LIBRARY_CATALOG_SUBTREE + ":" + scanRunId,
                currentJobId
        )) {
            return;
        }
        if (backgroundJobService.hasActiveJobs(
                ScanJobTypes.ASSET_IDENTITY_BACKFILL,
                ScanJobTypes.ASSET_IDENTITY_BACKFILL + ":" + scanRunId,
                currentJobId
        )) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            ScanRepository.ScanRunRecord run = scanRepository.findScanRun(scanRunId).orElse(null);
            if (run == null || !"running".equals(run.status())) {
                return;
            }
            ScanCounts counts = new ScanCounts();
            List<ScanRepository.ObservationInsert> observations = new ArrayList<>();
            for (LibraryRepository.LibraryRootRecord root : roots) {
                Path rootPath = Path.of(root.normalizedPath());
                if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath) || !Files.isExecutable(rootPath)) {
                    continue;
                }
                Path missingScope = catalogStartPath(rootPath, subtreePath);
                List<ScanRepository.AssetFileRecord> notSeen =
                        scanRepository.activeFilesNotSeenInRun(library.id(), root.id(), scanRunId);
                for (ScanRepository.AssetFileRecord activeFile : notSeen) {
                    if (!Path.of(activeFile.normalizedPath()).startsWith(missingScope)) {
                        continue;
                    }
                    if (exclusionMatcher.matches(rootPath, Path.of(activeFile.normalizedPath()))) {
                        continue;
                    }
                    scanRepository.markStatus(activeFile.id(), "missing", null);
                    observations.add(observation(
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
                }
            }
            scanRepository.createObservations(observations);
            scanRepository.incrementScanRunCounts(scanRunId, counts);
            scanRepository.completeScanRunFromCurrentCounts(scanRunId, OffsetDateTime.now());
        });
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

    private record FileCandidate(
            String path,
            String normalizedPath,
            String fileName,
            long size,
            OffsetDateTime modifiedAt
    ) {
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
