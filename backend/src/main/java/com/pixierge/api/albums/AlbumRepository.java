package com.pixierge.api.albums;

import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetFiles;
import com.querydsl.core.QueryFlag;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AlbumRepository {
    private static final QAlbums ALBUMS = QAlbums.albums;
    private static final QAlbumItems ITEMS = QAlbumItems.albumItems;
    private static final QAssetFiles FILES = QAssetFiles.assetFiles;
    private final SQLQueryFactory queryFactory;

    AlbumRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    List<AlbumRecord> list(UUID userId) {
        return queryFactory.select(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt,
                        ALBUMS.updatedAt, ITEMS.assetId.countDistinct(), ITEMS.sourceLibraryId.countDistinct(), FILES.fileName.min())
                .from(ALBUMS).leftJoin(ITEMS).on(ITEMS.albumId.eq(ALBUMS.id))
                .leftJoin(FILES).on(FILES.assetId.eq(ALBUMS.coverAssetId))
                .where(ALBUMS.ownerUserId.eq(userId).and(ALBUMS.kind.eq(AlbumKind.USER)))
                .groupBy(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt, ALBUMS.updatedAt)
                .orderBy(ALBUMS.updatedAt.desc(), ALBUMS.name.lower().asc()).fetch().stream().map(this::record).toList();
    }

    Optional<AlbumRecord> find(UUID albumId, UUID userId) {
        return queryFactory.select(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt,
                        ALBUMS.updatedAt, ITEMS.assetId.countDistinct(), ITEMS.sourceLibraryId.countDistinct(), FILES.fileName.min())
                .from(ALBUMS).leftJoin(ITEMS).on(ITEMS.albumId.eq(ALBUMS.id))
                .leftJoin(FILES).on(FILES.assetId.eq(ALBUMS.coverAssetId))
                .where(ALBUMS.id.eq(albumId).and(ALBUMS.ownerUserId.eq(userId)))
                .groupBy(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt, ALBUMS.updatedAt)
                .fetch().stream().findFirst().map(this::record);
    }

    Optional<AlbumRecord> findByKind(UUID userId, String kind) {
        return queryFactory.select(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt,
                        ALBUMS.updatedAt, ITEMS.assetId.countDistinct(), ITEMS.sourceLibraryId.countDistinct(), FILES.fileName.min())
                .from(ALBUMS).leftJoin(ITEMS).on(ITEMS.albumId.eq(ALBUMS.id))
                .leftJoin(FILES).on(FILES.assetId.eq(ALBUMS.coverAssetId))
                .where(ALBUMS.ownerUserId.eq(userId).and(ALBUMS.kind.eq(kind)))
                .groupBy(ALBUMS.id, ALBUMS.name, ALBUMS.coverAssetId, ALBUMS.kind, ALBUMS.createdAt, ALBUMS.updatedAt)
                .fetch().stream().findFirst().map(this::record);
    }

    boolean ownsAll(UUID userId, List<UUID> albumIds) {
        Long count = queryFactory.select(ALBUMS.id.count()).from(ALBUMS)
                .where(ALBUMS.ownerUserId.eq(userId).and(ALBUMS.id.in(albumIds))).fetchOne();
        return count != null && count == albumIds.size();
    }

    UUID create(UUID userId, String name) {
        return create(userId, name, AlbumKind.USER);
    }

    UUID create(UUID userId, String name, String kind) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        queryFactory.insert(ALBUMS).set(ALBUMS.id, id).set(ALBUMS.ownerUserId, userId).set(ALBUMS.name, name)
                .set(ALBUMS.kind, kind).set(ALBUMS.createdAt, now).set(ALBUMS.updatedAt, now).execute();
        return id;
    }

    Optional<UUID> createStarredIfAbsent(UUID userId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        boolean inserted = queryFactory.insert(ALBUMS).set(ALBUMS.id, id).set(ALBUMS.ownerUserId, userId)
                .set(ALBUMS.name, AlbumKind.STARRED_NAME).set(ALBUMS.kind, AlbumKind.STARRED)
                .set(ALBUMS.createdAt, now).set(ALBUMS.updatedAt, now)
                .addFlag(QueryFlag.Position.END, " ON CONFLICT (owner_user_id) WHERE (kind = 'starred') DO NOTHING")
                .execute() > 0;
        return inserted ? Optional.of(id) : Optional.empty();
    }

    boolean update(UUID id, UUID userId, String name, UUID coverAssetId) {
        var update = queryFactory.update(ALBUMS).set(ALBUMS.updatedAt, OffsetDateTime.now());
        if (name != null) {
            update.set(ALBUMS.name, name);
        }
        if (coverAssetId != null) {
            update.set(ALBUMS.coverAssetId, coverAssetId);
        }
        return update.where(ALBUMS.id.eq(id).and(ALBUMS.ownerUserId.eq(userId))).execute() > 0;
    }

    boolean delete(UUID id, UUID userId) {
        return queryFactory.delete(ALBUMS).where(ALBUMS.id.eq(id).and(ALBUMS.ownerUserId.eq(userId))).execute() > 0;
    }

    int nextPosition(UUID albumId) {
        Integer max = queryFactory.select(ITEMS.position.max()).from(ITEMS).where(ITEMS.albumId.eq(albumId)).fetchOne();
        return max == null ? 0 : max + 1;
    }

    boolean add(UUID albumId, UUID assetId, UUID libraryId, int position, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean inserted = queryFactory.insert(ITEMS).set(ITEMS.albumId, albumId).set(ITEMS.assetId, assetId)
                .set(ITEMS.sourceLibraryId, libraryId).set(ITEMS.position, position).set(ITEMS.addedBy, userId)
                .set(ITEMS.createdAt, now).set(ITEMS.updatedAt, now)
                .addFlag(QueryFlag.Position.END, " ON CONFLICT DO NOTHING")
                .execute() > 0;
        if (inserted) {
            queryFactory.update(ALBUMS).set(ALBUMS.updatedAt, now).where(ALBUMS.id.eq(albumId)).execute();
        }
        return inserted;
    }

    void deleteItems(UUID albumId, List<UUID> assetIds) {
        if (queryFactory.delete(ITEMS).where(ITEMS.albumId.eq(albumId).and(ITEMS.assetId.in(assetIds))).execute() > 0) {
            queryFactory.update(ALBUMS).set(ALBUMS.updatedAt, OffsetDateTime.now()).where(ALBUMS.id.eq(albumId)).execute();
        }
    }

    private AlbumRecord record(com.querydsl.core.Tuple row) {
        Long itemCount = row.get(ITEMS.assetId.countDistinct());
        Long libraryCount = row.get(ITEMS.sourceLibraryId.countDistinct());
        return new AlbumRecord(row.get(ALBUMS.id), row.get(ALBUMS.name), row.get(ALBUMS.coverAssetId),
                row.get(FILES.fileName.min()), row.get(ALBUMS.kind), Math.toIntExact(itemCount == null ? 0 : itemCount),
                Math.toIntExact(libraryCount == null ? 0 : libraryCount), row.get(ALBUMS.createdAt), row.get(ALBUMS.updatedAt));
    }

    record AlbumRecord(UUID id, String name, UUID coverAssetId, String coverFileName, String kind,
                       int itemCount, int sourceLibraryCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
