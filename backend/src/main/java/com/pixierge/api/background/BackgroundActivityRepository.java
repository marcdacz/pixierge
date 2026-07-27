package com.pixierge.api.background;

import com.pixierge.api.db.QFileActivityEvents;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
class BackgroundActivityRepository {
    private static final QFileActivityEvents EVENTS = QFileActivityEvents.fileActivityEvents;
    private final SQLQueryFactory queryFactory;

    BackgroundActivityRepository(SQLQueryFactory queryFactory) { this.queryFactory = queryFactory; }

    @Transactional(readOnly = true)
    List<BackgroundFileActivityRow> recentFileActivity(int limit) {
        return searchFileActivity(null, null, null, null, 0, limit).items();
    }

    @Transactional(readOnly = true)
    PersistedFileActivityPage searchFileActivity(String q, Collection<String> statuses, OffsetDateTime from, OffsetDateTime to, int offset, int limit) {
        BooleanBuilder where = where(q, statuses, from, to);
        Long count = queryFactory.select(EVENTS.id.count()).from(EVENTS).where(where).fetchOne();
        int totalCount = Math.toIntExact(count == null ? 0L : count);
        if (totalCount == 0 || limit <= 0) return new PersistedFileActivityPage(List.of(), totalCount);
        List<BackgroundFileActivityRow> items = queryFactory
                .select(EVENTS.assetId, EVENTS.path, EVENTS.status, EVENTS.occurredAt, EVENTS.message, EVENTS.durationMs)
                .from(EVENTS).where(where).orderBy(EVENTS.occurredAt.desc(), EVENTS.path.asc())
                .offset(Math.max(0, offset)).limit(Math.max(0, limit)).fetch().stream()
                .map(row -> new BackgroundFileActivityRow(row.get(EVENTS.assetId), row.get(EVENTS.path), row.get(EVENTS.status), row.get(EVENTS.occurredAt), row.get(EVENTS.message), row.get(EVENTS.durationMs)))
                .toList();
        return new PersistedFileActivityPage(items, totalCount);
    }

    @Transactional
    int clear() { return (int) queryFactory.delete(EVENTS).execute(); }

    @Transactional
    long deleteOlderThan(OffsetDateTime cutoff) { return queryFactory.delete(EVENTS).where(EVENTS.occurredAt.lt(cutoff)).execute(); }

    @Transactional
    void create(UUID assetId, String path, String status, OffsetDateTime occurredAt, String message, Long durationMs, UUID jobId, String batchLabel) {
        queryFactory.insert(EVENTS).set(EVENTS.id, UUID.randomUUID()).set(EVENTS.assetId, assetId).set(EVENTS.path, path)
                .set(EVENTS.status, status).set(EVENTS.occurredAt, occurredAt).set(EVENTS.message, message)
                .set(EVENTS.durationMs, durationMs).set(EVENTS.jobId, jobId).set(EVENTS.batchLabel, batchLabel).execute();
    }

    private BooleanBuilder where(String q, Collection<String> statuses, OffsetDateTime from, OffsetDateTime to) {
        BooleanBuilder where = new BooleanBuilder();
        if (q != null && !q.isBlank()) where.and(EVENTS.path.lower().contains(q.trim().toLowerCase(Locale.ROOT)));
        Set<String> normalized = statuses == null ? Set.of() : statuses.stream().flatMap(value -> Stream.of(value.split(","))).map(String::trim)
                .filter(value -> !value.isEmpty()).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!normalized.isEmpty()) where.and(EVENTS.status.in(normalized));
        if (from != null) where.and(EVENTS.occurredAt.goe(from));
        if (to != null) where.and(EVENTS.occurredAt.lt(to));
        return where;
    }

    record PersistedFileActivityPage(List<BackgroundFileActivityRow> items, int totalCount) { }
}
