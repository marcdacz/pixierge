package com.pixierge.api.scans;

import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QFileObservations;
import com.pixierge.api.db.QScanErrors;
import com.pixierge.api.db.QScanRuns;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ScanRepository {

    private static final QAssets ASSETS = QAssets.assets;
    private static final QAssetFiles ASSET_FILES = QAssetFiles.assetFiles;
    private static final QFileObservations FILE_OBSERVATIONS = QFileObservations.fileObservations;
    private static final QScanRuns SCAN_RUNS = QScanRuns.scanRuns;
    private static final QScanErrors SCAN_ERRORS = QScanErrors.scanErrors;

    private final SQLQueryFactory queryFactory;

    ScanRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    UUID createScanRun(UUID libraryId, UUID rootId, UUID requestedBy, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        queryFactory.insert(SCAN_RUNS)
                .set(SCAN_RUNS.id, id)
                .set(SCAN_RUNS.libraryId, libraryId)
                .set(SCAN_RUNS.rootId, rootId)
                .set(SCAN_RUNS.requestedBy, requestedBy)
                .set(SCAN_RUNS.status, "running")
                .set(SCAN_RUNS.startedAt, now)
                .execute();
        return id;
    }

    void completeScanRun(UUID scanRunId, ScanCounts counts, OffsetDateTime completedAt) {
        String status = counts.errorCount() > 0 ? "completed_with_errors" : "completed";
        queryFactory.update(SCAN_RUNS)
                .set(SCAN_RUNS.status, status)
                .set(SCAN_RUNS.completedAt, completedAt)
                .set(SCAN_RUNS.scannedFileCount, counts.scannedFileCount())
                .set(SCAN_RUNS.addedCount, counts.addedCount())
                .set(SCAN_RUNS.unchangedCount, counts.unchangedCount())
                .set(SCAN_RUNS.movedCount, counts.movedCount())
                .set(SCAN_RUNS.modifiedCount, counts.modifiedCount())
                .set(SCAN_RUNS.duplicateCount, counts.duplicateCount())
                .set(SCAN_RUNS.missingCount, counts.missingCount())
                .set(SCAN_RUNS.reappearedCount, counts.reappearedCount())
                .set(SCAN_RUNS.errorCount, counts.errorCount())
                .where(SCAN_RUNS.id.eq(scanRunId))
                .execute();
    }

    void updateScanRunProgress(UUID scanRunId, ScanCounts counts) {
        queryFactory.update(SCAN_RUNS)
                .set(SCAN_RUNS.scannedFileCount, counts.scannedFileCount())
                .set(SCAN_RUNS.addedCount, counts.addedCount())
                .set(SCAN_RUNS.unchangedCount, counts.unchangedCount())
                .set(SCAN_RUNS.movedCount, counts.movedCount())
                .set(SCAN_RUNS.modifiedCount, counts.modifiedCount())
                .set(SCAN_RUNS.duplicateCount, counts.duplicateCount())
                .set(SCAN_RUNS.missingCount, counts.missingCount())
                .set(SCAN_RUNS.reappearedCount, counts.reappearedCount())
                .set(SCAN_RUNS.errorCount, counts.errorCount())
                .where(SCAN_RUNS.id.eq(scanRunId).and(SCAN_RUNS.status.eq("running")))
                .execute();
    }

    void failScanRun(UUID scanRunId, OffsetDateTime completedAt) {
        queryFactory.update(SCAN_RUNS)
                .set(SCAN_RUNS.status, "failed")
                .set(SCAN_RUNS.completedAt, completedAt)
                .where(SCAN_RUNS.id.eq(scanRunId).and(SCAN_RUNS.status.eq("running")))
                .execute();
    }

    Optional<ScanRunRecord> findScanRun(UUID scanRunId) {
        Tuple row = selectScanRuns()
                .where(SCAN_RUNS.id.eq(scanRunId))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toScanRunRecord);
    }

    List<ScanRunRecord> listScanRuns(UUID libraryId) {
        return selectScanRuns()
                .where(SCAN_RUNS.libraryId.eq(libraryId))
                .orderBy(SCAN_RUNS.startedAt.desc())
                .limit(20)
                .fetch()
                .stream()
                .map(this::toScanRunRecord)
                .toList();
    }

    List<ScanErrorRecord> errorsForScan(UUID scanRunId) {
        return queryFactory
                .select(SCAN_ERRORS.id, SCAN_ERRORS.path, SCAN_ERRORS.errorCode, SCAN_ERRORS.message, SCAN_ERRORS.createdAt)
                .from(SCAN_ERRORS)
                .where(SCAN_ERRORS.scanRunId.eq(scanRunId))
                .orderBy(SCAN_ERRORS.createdAt.asc())
                .fetch()
                .stream()
                .map(row -> new ScanErrorRecord(
                        row.get(SCAN_ERRORS.id),
                        row.get(SCAN_ERRORS.path),
                        row.get(SCAN_ERRORS.errorCode),
                        row.get(SCAN_ERRORS.message),
                        row.get(SCAN_ERRORS.createdAt)
                ))
                .toList();
    }

    Optional<AssetFileRecord> findActiveFileByPath(UUID libraryId, String normalizedPath) {
        Tuple row = selectAssetFiles()
                .where(ASSET_FILES.libraryId.eq(libraryId)
                        .and(ASSET_FILES.normalizedPath.eq(normalizedPath))
                        .and(ASSET_FILES.status.eq("active")))
                .fetchOne();
        return Optional.ofNullable(row).map(this::toAssetFileRecord);
    }

    Optional<AssetFileRecord> findActiveFileByHash(UUID libraryId, String contentHash) {
        Tuple row = selectAssetFiles()
                .where(ASSET_FILES.libraryId.eq(libraryId)
                        .and(ASSET_FILES.contentHash.eq(contentHash))
                        .and(ASSET_FILES.status.eq("active")))
                .orderBy(ASSET_FILES.lastObservedAt.desc())
                .fetchFirst();
        return Optional.ofNullable(row).map(this::toAssetFileRecord);
    }

    Optional<AssetFileRecord> findMissingFileByPathAndHash(UUID libraryId, String normalizedPath, String contentHash) {
        Tuple row = selectAssetFiles()
                .where(ASSET_FILES.libraryId.eq(libraryId)
                        .and(ASSET_FILES.normalizedPath.eq(normalizedPath))
                        .and(ASSET_FILES.contentHash.eq(contentHash))
                        .and(ASSET_FILES.status.eq("missing")))
                .orderBy(ASSET_FILES.lastObservedAt.desc())
                .fetchFirst();
        return Optional.ofNullable(row).map(this::toAssetFileRecord);
    }

    List<AssetFileRecord> activeFilesForRoot(UUID libraryId, UUID rootId) {
        return selectAssetFiles()
                .where(ASSET_FILES.libraryId.eq(libraryId)
                        .and(ASSET_FILES.rootId.eq(rootId))
                        .and(ASSET_FILES.status.eq("active")))
                .fetch()
                .stream()
                .map(this::toAssetFileRecord)
                .toList();
    }

    Optional<UUID> findAssetByHash(String contentHash) {
        UUID id = queryFactory
                .select(ASSETS.id)
                .from(ASSETS)
                .where(ASSETS.contentHash.eq(contentHash))
                .fetchOne();
        return Optional.ofNullable(id);
    }

    UUID createAsset(String contentHash, String mediaType, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        queryFactory.insert(ASSETS)
                .set(ASSETS.id, id)
                .set(ASSETS.contentHash, contentHash)
                .set(ASSETS.mediaType, mediaType)
                .set(ASSETS.availableFileCount, 0)
                .set(ASSETS.firstObservedAt, now)
                .set(ASSETS.lastObservedAt, now)
                .execute();
        return id;
    }

    void touchAsset(UUID assetId, OffsetDateTime now) {
        queryFactory.update(ASSETS)
                .set(ASSETS.lastObservedAt, now)
                .where(ASSETS.id.eq(assetId))
                .execute();
    }

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
        queryFactory.insert(ASSET_FILES)
                .set(ASSET_FILES.id, id)
                .set(ASSET_FILES.assetId, assetId)
                .set(ASSET_FILES.libraryId, libraryId)
                .set(ASSET_FILES.rootId, rootId)
                .set(ASSET_FILES.path, path)
                .set(ASSET_FILES.normalizedPath, normalizedPath)
                .set(ASSET_FILES.fileName, fileName)
                .set(ASSET_FILES.sizeBytes, sizeBytes)
                .set(ASSET_FILES.modifiedAt, modifiedAt)
                .set(ASSET_FILES.contentHash, contentHash)
                .set(ASSET_FILES.status, "active")
                .set(ASSET_FILES.firstObservedAt, now)
                .set(ASSET_FILES.lastObservedAt, now)
                .set(ASSET_FILES.lastSeenScanRunId, scanRunId)
                .execute();
        recomputeAssetAvailability(assetId);
        return id;
    }

    void updateActiveFileSeen(
            UUID assetFileId,
            long sizeBytes,
            OffsetDateTime modifiedAt,
            UUID scanRunId,
            OffsetDateTime now
    ) {
        queryFactory.update(ASSET_FILES)
                .set(ASSET_FILES.sizeBytes, sizeBytes)
                .set(ASSET_FILES.modifiedAt, modifiedAt)
                .set(ASSET_FILES.lastObservedAt, now)
                .set(ASSET_FILES.lastSeenScanRunId, scanRunId)
                .where(ASSET_FILES.id.eq(assetFileId))
                .execute();
    }

    void reviveMissingFile(
            UUID assetFileId,
            long sizeBytes,
            OffsetDateTime modifiedAt,
            UUID scanRunId,
            OffsetDateTime now
    ) {
        UUID assetId = queryFactory.select(ASSET_FILES.assetId)
                .from(ASSET_FILES)
                .where(ASSET_FILES.id.eq(assetFileId))
                .fetchOne();
        queryFactory.update(ASSET_FILES)
                .set(ASSET_FILES.status, "active")
                .set(ASSET_FILES.sizeBytes, sizeBytes)
                .set(ASSET_FILES.modifiedAt, modifiedAt)
                .set(ASSET_FILES.lastObservedAt, now)
                .set(ASSET_FILES.lastSeenScanRunId, scanRunId)
                .where(ASSET_FILES.id.eq(assetFileId))
                .execute();
        if (assetId != null) {
            recomputeAssetAvailability(assetId);
        }
    }

    void markStatus(UUID assetFileId, String status, UUID replacedByFileId) {
        UUID assetId = queryFactory.select(ASSET_FILES.assetId)
                .from(ASSET_FILES)
                .where(ASSET_FILES.id.eq(assetFileId))
                .fetchOne();
        queryFactory.update(ASSET_FILES)
                .set(ASSET_FILES.status, status)
                .set(ASSET_FILES.replacedByFileId, replacedByFileId)
                .set(ASSET_FILES.lastObservedAt, OffsetDateTime.now())
                .where(ASSET_FILES.id.eq(assetFileId))
                .execute();
        if (assetId != null) {
            recomputeAssetAvailability(assetId);
        }
    }

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
        queryFactory.insert(FILE_OBSERVATIONS)
                .set(FILE_OBSERVATIONS.id, UUID.randomUUID())
                .set(FILE_OBSERVATIONS.scanRunId, scanRunId)
                .set(FILE_OBSERVATIONS.libraryId, libraryId)
                .set(FILE_OBSERVATIONS.rootId, rootId)
                .set(FILE_OBSERVATIONS.assetId, assetId)
                .set(FILE_OBSERVATIONS.assetFileId, assetFileId)
                .set(FILE_OBSERVATIONS.path, path)
                .set(FILE_OBSERVATIONS.normalizedPath, normalizedPath)
                .set(FILE_OBSERVATIONS.sizeBytes, sizeBytes)
                .set(FILE_OBSERVATIONS.modifiedAt, modifiedAt)
                .set(FILE_OBSERVATIONS.partialHash, partialHash)
                .set(FILE_OBSERVATIONS.contentHash, contentHash)
                .set(FILE_OBSERVATIONS.result, result)
                .execute();
    }

    void createError(UUID scanRunId, UUID libraryId, UUID rootId, String path, String errorCode, String message) {
        queryFactory.insert(SCAN_ERRORS)
                .set(SCAN_ERRORS.id, UUID.randomUUID())
                .set(SCAN_ERRORS.scanRunId, scanRunId)
                .set(SCAN_ERRORS.libraryId, libraryId)
                .set(SCAN_ERRORS.rootId, rootId)
                .set(SCAN_ERRORS.path, path)
                .set(SCAN_ERRORS.errorCode, errorCode)
                .set(SCAN_ERRORS.message, message)
                .set(SCAN_ERRORS.createdAt, OffsetDateTime.now())
                .execute();
    }

    private void recomputeAssetAvailability(UUID assetId) {
        Long count = queryFactory.select(ASSET_FILES.id.count())
                .from(ASSET_FILES)
                .where(ASSET_FILES.assetId.eq(assetId).and(ASSET_FILES.status.eq("active")))
                .fetchOne();
        queryFactory.update(ASSETS)
                .set(ASSETS.availableFileCount, count == null ? 0 : count.intValue())
                .where(ASSETS.id.eq(assetId))
                .execute();
    }

    private SQLQuery<Tuple> selectScanRuns() {
        return queryFactory.select(
                        SCAN_RUNS.id,
                        SCAN_RUNS.libraryId,
                        SCAN_RUNS.rootId,
                        SCAN_RUNS.status,
                        SCAN_RUNS.startedAt,
                        SCAN_RUNS.completedAt,
                        SCAN_RUNS.scannedFileCount,
                        SCAN_RUNS.addedCount,
                        SCAN_RUNS.unchangedCount,
                        SCAN_RUNS.movedCount,
                        SCAN_RUNS.modifiedCount,
                        SCAN_RUNS.duplicateCount,
                        SCAN_RUNS.missingCount,
                        SCAN_RUNS.reappearedCount,
                        SCAN_RUNS.errorCount
                )
                .from(SCAN_RUNS);
    }

    private ScanRunRecord toScanRunRecord(Tuple row) {
        return new ScanRunRecord(
                row.get(SCAN_RUNS.id),
                row.get(SCAN_RUNS.libraryId),
                row.get(SCAN_RUNS.rootId),
                row.get(SCAN_RUNS.status),
                row.get(SCAN_RUNS.startedAt),
                row.get(SCAN_RUNS.completedAt),
                value(row.get(SCAN_RUNS.scannedFileCount)),
                value(row.get(SCAN_RUNS.addedCount)),
                value(row.get(SCAN_RUNS.unchangedCount)),
                value(row.get(SCAN_RUNS.movedCount)),
                value(row.get(SCAN_RUNS.modifiedCount)),
                value(row.get(SCAN_RUNS.duplicateCount)),
                value(row.get(SCAN_RUNS.missingCount)),
                value(row.get(SCAN_RUNS.reappearedCount)),
                value(row.get(SCAN_RUNS.errorCount))
        );
    }

    private SQLQuery<Tuple> selectAssetFiles() {
        return queryFactory.select(
                        ASSET_FILES.id,
                        ASSET_FILES.assetId,
                        ASSET_FILES.libraryId,
                        ASSET_FILES.rootId,
                        ASSET_FILES.path,
                        ASSET_FILES.normalizedPath,
                        ASSET_FILES.fileName,
                        ASSET_FILES.sizeBytes,
                        ASSET_FILES.modifiedAt,
                        ASSET_FILES.contentHash,
                        ASSET_FILES.status
                )
                .from(ASSET_FILES);
    }

    private AssetFileRecord toAssetFileRecord(Tuple row) {
        return new AssetFileRecord(
                row.get(ASSET_FILES.id),
                row.get(ASSET_FILES.assetId),
                row.get(ASSET_FILES.libraryId),
                row.get(ASSET_FILES.rootId),
                row.get(ASSET_FILES.path),
                row.get(ASSET_FILES.normalizedPath),
                row.get(ASSET_FILES.fileName),
                row.get(ASSET_FILES.sizeBytes),
                row.get(ASSET_FILES.modifiedAt),
                row.get(ASSET_FILES.contentHash),
                row.get(ASSET_FILES.status)
        );
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    record AssetFileRecord(
            UUID id,
            UUID assetId,
            UUID libraryId,
            UUID rootId,
            String path,
            String normalizedPath,
            String fileName,
            long sizeBytes,
            OffsetDateTime modifiedAt,
            String contentHash,
            String status
    ) {
    }

    record ScanRunRecord(
            UUID id,
            UUID libraryId,
            UUID rootId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            int scannedFileCount,
            int addedCount,
            int unchangedCount,
            int movedCount,
            int modifiedCount,
            int duplicateCount,
            int missingCount,
            int reappearedCount,
            int errorCount
    ) {
    }

    record ScanErrorRecord(
            UUID id,
            String path,
            String errorCode,
            String message,
            OffsetDateTime createdAt
    ) {
    }
}
