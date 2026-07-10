package com.pixierge.api.assets;

import com.pixierge.api.db.QThumbnails;
import com.querydsl.core.QueryFlag;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class ThumbnailRepository {

    private static final QThumbnails THUMBNAILS = QThumbnails.thumbnails;

    private final SQLQueryFactory queryFactory;

    ThumbnailRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    Optional<ThumbnailRow> findByCacheInput(ThumbnailCacheInput input) {
        return queryFactory.select(
                        THUMBNAILS.id,
                        THUMBNAILS.assetId,
                        THUMBNAILS.contentHash,
                        THUMBNAILS.thumbnailType,
                        THUMBNAILS.width,
                        THUMBNAILS.height,
                        THUMBNAILS.format,
                        THUMBNAILS.generatorVersion,
                        THUMBNAILS.configVersion,
                        THUMBNAILS.cacheKey,
                        THUMBNAILS.relativePath,
                        THUMBNAILS.byteSize,
                        THUMBNAILS.placeholder,
                        THUMBNAILS.status,
                        THUMBNAILS.errorMessage,
                        THUMBNAILS.generatedAt,
                        THUMBNAILS.updatedAt
                )
                .from(THUMBNAILS)
                .where(
                        THUMBNAILS.contentHash.eq(input.contentHash()),
                        THUMBNAILS.thumbnailType.eq(input.thumbnailType()),
                        THUMBNAILS.width.eq(input.width()),
                        THUMBNAILS.height.eq(input.height()),
                        THUMBNAILS.format.eq(input.format()),
                        THUMBNAILS.generatorVersion.eq(input.generatorVersion()),
                        THUMBNAILS.configVersion.eq(input.configVersion())
                )
                .fetch()
                .stream()
                .findFirst()
                .map(this::toThumbnailRow);
    }

    Map<String, ThumbnailRow> findReadyRowsByContentHashes(
            Collection<String> contentHashes,
            String thumbnailType,
            int width,
            int height,
            String format,
            String generatorVersion,
            String configVersion
    ) {
        if (contentHashes.isEmpty()) {
            return Map.of();
        }
        Map<String, ThumbnailRow> rowsByHash = new LinkedHashMap<>();
        queryFactory.select(
                        THUMBNAILS.id,
                        THUMBNAILS.assetId,
                        THUMBNAILS.contentHash,
                        THUMBNAILS.thumbnailType,
                        THUMBNAILS.width,
                        THUMBNAILS.height,
                        THUMBNAILS.format,
                        THUMBNAILS.generatorVersion,
                        THUMBNAILS.configVersion,
                        THUMBNAILS.cacheKey,
                        THUMBNAILS.relativePath,
                        THUMBNAILS.byteSize,
                        THUMBNAILS.placeholder,
                        THUMBNAILS.status,
                        THUMBNAILS.errorMessage,
                        THUMBNAILS.generatedAt,
                        THUMBNAILS.updatedAt
                )
                .from(THUMBNAILS)
                .where(
                        THUMBNAILS.contentHash.in(contentHashes),
                        THUMBNAILS.thumbnailType.eq(thumbnailType),
                        THUMBNAILS.width.eq(width),
                        THUMBNAILS.height.eq(height),
                        THUMBNAILS.format.eq(format),
                        THUMBNAILS.generatorVersion.eq(generatorVersion),
                        THUMBNAILS.configVersion.eq(configVersion),
                        THUMBNAILS.status.eq("ready")
                )
                .fetch()
                .stream()
                .map(this::toThumbnailRow)
                .forEach(row -> rowsByHash.put(row.contentHash(), row));
        return rowsByHash;
    }

    List<ThumbnailRow> findStaleRows(String generatorVersion, String configVersion) {
        return queryFactory.select(
                        THUMBNAILS.id,
                        THUMBNAILS.assetId,
                        THUMBNAILS.contentHash,
                        THUMBNAILS.thumbnailType,
                        THUMBNAILS.width,
                        THUMBNAILS.height,
                        THUMBNAILS.format,
                        THUMBNAILS.generatorVersion,
                        THUMBNAILS.configVersion,
                        THUMBNAILS.cacheKey,
                        THUMBNAILS.relativePath,
                        THUMBNAILS.byteSize,
                        THUMBNAILS.placeholder,
                        THUMBNAILS.status,
                        THUMBNAILS.errorMessage,
                        THUMBNAILS.generatedAt,
                        THUMBNAILS.updatedAt
                )
                .from(THUMBNAILS)
                .where(THUMBNAILS.generatorVersion.ne(generatorVersion)
                        .or(THUMBNAILS.configVersion.ne(configVersion)))
                .fetch()
                .stream()
                .map(this::toThumbnailRow)
                .toList();
    }

    void upsertReadyRow(UUID assetId, ThumbnailCacheInput input, String relativePath, long byteSize, String placeholder) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long updated = queryFactory.update(THUMBNAILS)
                .set(THUMBNAILS.assetId, assetId)
                .set(THUMBNAILS.cacheKey, input.cacheKey())
                .set(THUMBNAILS.relativePath, relativePath)
                .set(THUMBNAILS.byteSize, byteSize)
                .set(THUMBNAILS.placeholder, placeholder)
                .set(THUMBNAILS.status, "ready")
                .set(THUMBNAILS.errorMessage, (String) null)
                .set(THUMBNAILS.updatedAt, now)
                .set(THUMBNAILS.generatedAt, now)
                .where(
                        THUMBNAILS.contentHash.eq(input.contentHash()),
                        THUMBNAILS.thumbnailType.eq(input.thumbnailType()),
                        THUMBNAILS.width.eq(input.width()),
                        THUMBNAILS.height.eq(input.height()),
                        THUMBNAILS.format.eq(input.format()),
                        THUMBNAILS.generatorVersion.eq(input.generatorVersion()),
                        THUMBNAILS.configVersion.eq(input.configVersion())
                )
                .execute();

        if (updated == 0) {
            queryFactory.insert(THUMBNAILS)
                    .set(THUMBNAILS.id, UUID.randomUUID())
                    .set(THUMBNAILS.assetId, assetId)
                    .set(THUMBNAILS.contentHash, input.contentHash())
                    .set(THUMBNAILS.thumbnailType, input.thumbnailType())
                    .set(THUMBNAILS.width, input.width())
                    .set(THUMBNAILS.height, input.height())
                    .set(THUMBNAILS.format, input.format())
                    .set(THUMBNAILS.generatorVersion, input.generatorVersion())
                    .set(THUMBNAILS.configVersion, input.configVersion())
                    .set(THUMBNAILS.cacheKey, input.cacheKey())
                    .set(THUMBNAILS.relativePath, relativePath)
                    .set(THUMBNAILS.byteSize, byteSize)
                    .set(THUMBNAILS.placeholder, placeholder)
                    .set(THUMBNAILS.status, "ready")
                    .set(THUMBNAILS.createdAt, now)
                    .set(THUMBNAILS.updatedAt, now)
                    .set(THUMBNAILS.generatedAt, now)
                    .addFlag(QueryFlag.Position.END, " ON CONFLICT DO NOTHING")
                    .execute();
        }
    }

    void markMissing(UUID rowId) {
        queryFactory.update(THUMBNAILS)
                .set(THUMBNAILS.status, "missing")
                .set(THUMBNAILS.updatedAt, OffsetDateTime.now(ZoneOffset.UTC))
                .where(THUMBNAILS.id.eq(rowId))
                .execute();
    }

    private ThumbnailRow toThumbnailRow(Tuple row) {
        return new ThumbnailRow(
                row.get(THUMBNAILS.id),
                row.get(THUMBNAILS.assetId),
                row.get(THUMBNAILS.contentHash),
                row.get(THUMBNAILS.thumbnailType),
                row.get(THUMBNAILS.width),
                row.get(THUMBNAILS.height),
                row.get(THUMBNAILS.format),
                row.get(THUMBNAILS.generatorVersion),
                row.get(THUMBNAILS.configVersion),
                row.get(THUMBNAILS.cacheKey),
                row.get(THUMBNAILS.relativePath),
                row.get(THUMBNAILS.byteSize),
                row.get(THUMBNAILS.placeholder),
                row.get(THUMBNAILS.status),
                row.get(THUMBNAILS.errorMessage),
                row.get(THUMBNAILS.generatedAt),
                row.get(THUMBNAILS.updatedAt)
        );
    }

    void markStale(UUID rowId) {
        queryFactory.update(THUMBNAILS)
                .set(THUMBNAILS.status, "stale")
                .set(THUMBNAILS.updatedAt, OffsetDateTime.now(ZoneOffset.UTC))
                .where(THUMBNAILS.id.eq(rowId))
                .execute();
    }

    void deleteById(UUID rowId) {
        queryFactory.delete(THUMBNAILS)
                .where(THUMBNAILS.id.eq(rowId))
                .execute();
    }

    record ThumbnailCacheInput(
            String contentHash,
            String thumbnailType,
            int width,
            int height,
            String format,
            String generatorVersion,
            String configVersion,
            String cacheKey
    ) {
    }

    record ThumbnailRow(
            UUID id,
            UUID assetId,
            String contentHash,
            String thumbnailType,
            int width,
            int height,
            String format,
            String generatorVersion,
            String configVersion,
            String cacheKey,
            String relativePath,
            long byteSize,
            String placeholder,
            String status,
            String errorMessage,
            OffsetDateTime generatedAt,
            OffsetDateTime updatedAt
    ) {
    }
}
