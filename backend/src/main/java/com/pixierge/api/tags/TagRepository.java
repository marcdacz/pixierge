package com.pixierge.api.tags;

import com.pixierge.api.assets.AssetTagResponse;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QTags;
import com.querydsl.core.QueryFlag;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TagRepository {

    private static final QTags TAGS = QTags.tags;
    private static final QAssetTags ASSET_TAGS = QAssetTags.assetTags;

    private final SQLQueryFactory queryFactory;

    public TagRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<TagRecord> list(UUID ownerUserId) {
        return queryFactory.select(TAGS.id, TAGS.name, TAGS.createdAt, TAGS.updatedAt, ASSET_TAGS.assetId.countDistinct())
                .from(TAGS)
                .leftJoin(ASSET_TAGS).on(ASSET_TAGS.tagId.eq(TAGS.id))
                .where(TAGS.ownerUserId.eq(ownerUserId))
                .groupBy(TAGS.id, TAGS.name, TAGS.createdAt, TAGS.updatedAt)
                .orderBy(TAGS.name.lower().asc())
                .fetch().stream()
                .map(row -> new TagRecord(row.get(TAGS.id), row.get(TAGS.name), row.get(TAGS.createdAt),
                        row.get(TAGS.updatedAt), Math.toIntExact(row.get(ASSET_TAGS.assetId.countDistinct()))))
                .toList();
    }

    public Optional<TagRecord> find(UUID tagId, UUID ownerUserId) {
        return queryFactory.select(TAGS.id, TAGS.name, TAGS.createdAt, TAGS.updatedAt, ASSET_TAGS.assetId.countDistinct())
                .from(TAGS)
                .leftJoin(ASSET_TAGS).on(ASSET_TAGS.tagId.eq(TAGS.id))
                .where(TAGS.id.eq(tagId).and(TAGS.ownerUserId.eq(ownerUserId)))
                .groupBy(TAGS.id, TAGS.name, TAGS.createdAt, TAGS.updatedAt)
                .fetch().stream()
                .findFirst()
                .map(row -> new TagRecord(row.get(TAGS.id), row.get(TAGS.name), row.get(TAGS.createdAt),
                        row.get(TAGS.updatedAt), Math.toIntExact(row.get(ASSET_TAGS.assetId.countDistinct()))));
    }

    public boolean ownsAll(UUID ownerUserId, List<UUID> tagIds) {
        Long count = queryFactory.select(TAGS.id.count()).from(TAGS)
                .where(TAGS.ownerUserId.eq(ownerUserId).and(TAGS.id.in(tagIds))).fetchOne();
        return count != null && count == tagIds.size();
    }

    public UUID create(UUID ownerUserId, String name, String normalizedName) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        queryFactory.insert(TAGS).set(TAGS.id, id).set(TAGS.ownerUserId, ownerUserId)
                .set(TAGS.name, name).set(TAGS.normalizedName, normalizedName).set(TAGS.createdBy, ownerUserId)
                .set(TAGS.createdAt, now).set(TAGS.updatedAt, now).execute();
        return id;
    }

    public boolean rename(UUID id, UUID ownerUserId, String name, String normalizedName) {
        return queryFactory.update(TAGS).set(TAGS.name, name).set(TAGS.normalizedName, normalizedName)
                .set(TAGS.updatedAt, OffsetDateTime.now())
                .where(TAGS.id.eq(id).and(TAGS.ownerUserId.eq(ownerUserId))).execute() > 0;
    }

    public boolean delete(UUID id, UUID ownerUserId) {
        return queryFactory.delete(TAGS).where(TAGS.id.eq(id).and(TAGS.ownerUserId.eq(ownerUserId))).execute() > 0;
    }

    public void add(UUID tagId, UUID assetId, UUID libraryId, UUID userId) {
        queryFactory.insert(ASSET_TAGS).set(ASSET_TAGS.tagId, tagId).set(ASSET_TAGS.assetId, assetId)
                .set(ASSET_TAGS.sourceLibraryId, libraryId).set(ASSET_TAGS.addedBy, userId)
                .set(ASSET_TAGS.createdAt, OffsetDateTime.now())
                .addFlag(QueryFlag.Position.END, " ON CONFLICT DO NOTHING")
                .execute();
    }

    public void deleteAssignments(UUID ownerUserId, List<UUID> tagIds, List<UUID> assetIds) {
        queryFactory.delete(ASSET_TAGS).where(ASSET_TAGS.tagId.in(tagIds).and(ASSET_TAGS.assetId.in(assetIds))
                .and(ASSET_TAGS.tagId.in(queryFactory.select(TAGS.id).from(TAGS).where(TAGS.ownerUserId.eq(ownerUserId)))))
                .execute();
    }

    public List<AssetTagResponse> listAssetTags(UUID assetId, UUID ownerUserId) {
        return queryFactory.select(TAGS.id, TAGS.name).from(ASSET_TAGS)
                .join(TAGS).on(TAGS.id.eq(ASSET_TAGS.tagId))
                .where(ASSET_TAGS.assetId.eq(assetId).and(TAGS.ownerUserId.eq(ownerUserId)))
                .orderBy(TAGS.name.lower().asc()).fetch()
                .stream().map(row -> new AssetTagResponse(row.get(TAGS.id), row.get(TAGS.name))).toList();
    }

    public record TagRecord(UUID id, String name, OffsetDateTime createdAt, OffsetDateTime updatedAt, int assetCount) {
    }
}
