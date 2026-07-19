package com.pixierge.api.background;

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
    private static final String FAILED_STATUS = "failed";

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

        long observationCount = includeObservations ? countObservations(observationWhere) : 0L;
        long errorCount = includeErrors ? countErrors(errorWhere) : 0L;
        int totalCount = Math.toIntExact(observationCount + errorCount);
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
        List<BackgroundFileActivityRow> pageItems = Stream.concat(observations.stream(), errors.stream())
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

    private List<BackgroundFileActivityRow> fetchObservations(BooleanBuilder where, int limit) {
        return queryFactory
                .select(FILE_OBSERVATIONS.path, FILE_OBSERVATIONS.result, FILE_OBSERVATIONS.observedAt)
                .from(FILE_OBSERVATIONS)
                .where(where)
                .orderBy(FILE_OBSERVATIONS.observedAt.desc(), FILE_OBSERVATIONS.path.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new BackgroundFileActivityRow(
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

    private BackgroundFileActivityRow toErrorRow(Tuple row) {
        return new BackgroundFileActivityRow(
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
