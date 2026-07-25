package com.pixierge.api.background;

import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetMetadata;
import com.pixierge.api.db.QFileObservations;
import com.pixierge.api.db.QScanErrors;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
class BackgroundActivityRepository {

    private static final QFileObservations FILE_OBSERVATIONS = QFileObservations.fileObservations;
    private static final QScanErrors SCAN_ERRORS = QScanErrors.scanErrors;
    private static final QAssetFiles ASSET_FILES = QAssetFiles.assetFiles;
    private static final QAssetMetadata ASSET_METADATA = QAssetMetadata.assetMetadata;
    private static final String FAILED_STATUS = "failed";
    private static final Set<String> METADATA_ACTIVITY_STATUSES = Set.of("extracted", "unsupported", FAILED_STATUS);

    private final SQLQueryFactory queryFactory;

    BackgroundActivityRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Transactional(readOnly = true)
    List<BackgroundFileActivityRow> recentFileActivity(int limit) {
        return searchFileActivity(null, null, null, null, 0, limit).items();
    }

    @Transactional(readOnly = true)
    PersistedFileActivityPage searchFileActivity(
            String q,
            Collection<String> statuses,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedTo,
            int offset,
            int limit
    ) {
        int normalizedOffset = Math.max(0, offset);
        int normalizedLimit = Math.max(0, limit);
        String normalizedQuery = normalizeQuery(q);
        Set<String> normalizedStatuses = normalizeStatuses(statuses);
        boolean includeObservations = normalizedStatuses == null
                || normalizedStatuses.stream().anyMatch(status -> !FAILED_STATUS.equals(status));
        boolean includeErrors = normalizedStatuses == null || normalizedStatuses.contains(FAILED_STATUS);
        List<String> metadataStatuses = normalizedStatuses == null
                ? List.copyOf(METADATA_ACTIVITY_STATUSES)
                : normalizedStatuses.stream().filter(METADATA_ACTIVITY_STATUSES::contains).toList();
        boolean includeMetadata = !metadataStatuses.isEmpty();

        List<String> observationStatuses = normalizedStatuses == null
                ? null
                : normalizedStatuses.stream().filter(status -> !FAILED_STATUS.equals(status)).toList();

        BooleanBuilder observationWhere = observationWhere(
                normalizedQuery,
                observationStatuses,
                updatedFrom,
                updatedTo
        );
        BooleanBuilder errorWhere = errorWhere(normalizedQuery, updatedFrom, updatedTo);
        BooleanBuilder metadataWhere = metadataWhere(normalizedQuery, metadataStatuses, updatedFrom, updatedTo);

        long observationCount = includeObservations ? countObservations(observationWhere) : 0L;
        long errorCount = includeErrors ? countErrors(errorWhere) : 0L;
        long metadataCount = includeMetadata ? countMetadata(metadataWhere) : 0L;
        int totalCount = Math.toIntExact(observationCount + errorCount + metadataCount);
        if (totalCount == 0 || normalizedLimit == 0) {
            return new PersistedFileActivityPage(List.of(), totalCount);
        }

        int fetchLimit = Math.max(1, normalizedOffset + normalizedLimit);
        List<BackgroundFileActivityRow> observations = includeObservations
                ? fetchObservations(observationWhere, fetchLimit)
                : List.of();
        List<BackgroundFileActivityRow> errors = includeErrors
                ? fetchErrors(errorWhere, fetchLimit)
                : List.of();
        List<BackgroundFileActivityRow> metadata = includeMetadata
                ? fetchMetadata(metadataWhere, fetchLimit)
                : List.of();
        List<BackgroundFileActivityRow> pageItems = Stream.of(observations, errors, metadata)
                .flatMap(Collection::stream)
                .sorted(Comparator
                        .comparing(BackgroundFileActivityRow::observedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(row -> row.path() == null ? "" : row.path(), String.CASE_INSENSITIVE_ORDER))
                .skip(normalizedOffset)
                .limit(normalizedLimit)
                .toList();
        return new PersistedFileActivityPage(pageItems, totalCount);
    }

    private long countObservations(BooleanBuilder where) {
        Long count = queryFactory.select(FILE_OBSERVATIONS.id.count())
                .from(FILE_OBSERVATIONS)
                .where(where)
                .fetchOne();
        return count == null ? 0L : count;
    }

    private long countErrors(BooleanBuilder where) {
        Long count = queryFactory.select(SCAN_ERRORS.id.count())
                .from(SCAN_ERRORS)
                .where(where)
                .fetchOne();
        return count == null ? 0L : count;
    }

    private long countMetadata(BooleanBuilder where) {
        Long count = queryFactory.select(ASSET_METADATA.assetId.countDistinct())
                .from(ASSET_METADATA)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ASSET_METADATA.assetId)
                        .and(ASSET_FILES.status.eq("active")))
                .where(where)
                .fetchOne();
        return count == null ? 0L : count;
    }

    private List<BackgroundFileActivityRow> fetchObservations(BooleanBuilder where, int limit) {
        return queryFactory
                .select(FILE_OBSERVATIONS.assetId, FILE_OBSERVATIONS.path, FILE_OBSERVATIONS.result, FILE_OBSERVATIONS.observedAt)
                .from(FILE_OBSERVATIONS)
                .where(where)
                .orderBy(FILE_OBSERVATIONS.observedAt.desc(), FILE_OBSERVATIONS.path.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new BackgroundFileActivityRow(
                        row.get(FILE_OBSERVATIONS.assetId),
                        row.get(FILE_OBSERVATIONS.path),
                        row.get(FILE_OBSERVATIONS.result),
                        row.get(FILE_OBSERVATIONS.observedAt),
                        null
                ))
                .toList();
    }

    private List<BackgroundFileActivityRow> fetchErrors(BooleanBuilder where, int limit) {
        return queryFactory
                .select(SCAN_ERRORS.path, SCAN_ERRORS.message, SCAN_ERRORS.createdAt)
                .from(SCAN_ERRORS)
                .where(where)
                .orderBy(SCAN_ERRORS.createdAt.desc(), SCAN_ERRORS.path.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(this::toErrorRow)
                .toList();
    }

    private BooleanBuilder observationWhere(
            String q,
            List<String> statuses,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedTo
    ) {
        BooleanBuilder where = new BooleanBuilder();
        if (statuses != null) {
            where.and(FILE_OBSERVATIONS.result.in(statuses));
        }
        if (q != null) {
            where.and(FILE_OBSERVATIONS.path.lower().contains(q));
        }
        if (updatedFrom != null) {
            where.and(FILE_OBSERVATIONS.observedAt.goe(updatedFrom));
        }
        if (updatedTo != null) {
            where.and(FILE_OBSERVATIONS.observedAt.lt(updatedTo));
        }
        return where;
    }

    private BooleanBuilder errorWhere(String q, OffsetDateTime updatedFrom, OffsetDateTime updatedTo) {
        BooleanBuilder where = new BooleanBuilder();
        if (q != null) {
            where.and(SCAN_ERRORS.path.lower().contains(q));
        }
        if (updatedFrom != null) {
            where.and(SCAN_ERRORS.createdAt.goe(updatedFrom));
        }
        if (updatedTo != null) {
            where.and(SCAN_ERRORS.createdAt.lt(updatedTo));
        }
        return where;
    }

    private BooleanBuilder metadataWhere(
            String q,
            List<String> statuses,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedTo
    ) {
        BooleanBuilder where = new BooleanBuilder(ASSET_METADATA.metadataStatus.in(statuses)
                .and(ASSET_METADATA.metadataExtractedAt.isNotNull())
                .and(ASSET_FILES.status.eq("active")));
        if (q != null) {
            where.and(ASSET_FILES.path.lower().contains(q));
        }
        if (updatedFrom != null) {
            where.and(ASSET_METADATA.metadataExtractedAt.goe(updatedFrom));
        }
        if (updatedTo != null) {
            where.and(ASSET_METADATA.metadataExtractedAt.lt(updatedTo));
        }
        return where;
    }

    private List<BackgroundFileActivityRow> fetchMetadata(BooleanBuilder where, int limit) {
        return queryFactory
                .select(
                        ASSET_METADATA.assetId,
                        ASSET_FILES.path.min(),
                        ASSET_METADATA.metadataStatus,
                        ASSET_METADATA.metadataExtractedAt,
                        ASSET_METADATA.metadataErrorMessage
                )
                .from(ASSET_METADATA)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ASSET_METADATA.assetId)
                        .and(ASSET_FILES.status.eq("active")))
                .where(where)
                .groupBy(
                        ASSET_METADATA.assetId,
                        ASSET_METADATA.metadataStatus,
                        ASSET_METADATA.metadataExtractedAt,
                        ASSET_METADATA.metadataErrorMessage
                )
                .orderBy(ASSET_METADATA.metadataExtractedAt.desc(), ASSET_FILES.path.min().asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new BackgroundFileActivityRow(
                        row.get(ASSET_METADATA.assetId),
                        row.get(ASSET_FILES.path.min()),
                        row.get(ASSET_METADATA.metadataStatus),
                        row.get(ASSET_METADATA.metadataExtractedAt),
                        row.get(ASSET_METADATA.metadataErrorMessage)
                ))
                .toList();
    }

    private BackgroundFileActivityRow toErrorRow(Tuple row) {
        return new BackgroundFileActivityRow(
                null,
                row.get(SCAN_ERRORS.path),
                FAILED_STATUS,
                row.get(SCAN_ERRORS.createdAt),
                row.get(SCAN_ERRORS.message)
        );
    }

    private static String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeStatuses(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        Set<String> normalized = statuses.stream()
                .flatMap(status -> Stream.of(status.split(",")))
                .map(String::trim)
                .filter(status -> !status.isEmpty())
                .map(status -> status.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return normalized.isEmpty() ? null : normalized;
    }

    record PersistedFileActivityPage(List<BackgroundFileActivityRow> items, int totalCount) {
    }
}
