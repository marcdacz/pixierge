package com.pixierge.api.assets;

import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetMetadata;
import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QAssets;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QLibraryRoots;
import com.pixierge.api.db.QSearchDocuments;
import com.pixierge.api.db.QTags;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.pixierge.api.assets.AssetConstants.AVAILABILITY_AVAILABLE;
import static com.pixierge.api.assets.AssetConstants.AVAILABILITY_MISSING;
import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_ACTIVE;
import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_MISSING;
import static com.pixierge.api.assets.AssetConstants.IMAGE_MIME_PREFIX;
import static com.pixierge.api.libraries.LibraryConstants.STATUS_ACTIVE;

@Repository
class AssetRepository {

    private static final QAssetFiles ASSET_FILES = QAssetFiles.assetFiles;
    private static final QAssets ASSETS = QAssets.assets;
    private static final QAssetMetadata ASSET_METADATA = QAssetMetadata.assetMetadata;
    private static final QAlbumItems ALBUM_ITEMS = QAlbumItems.albumItems;
    private static final QAssetTags ASSET_TAGS = QAssetTags.assetTags;
    private static final QLibraries LIBRARIES = QLibraries.libraries;
    private static final QLibraryMembers LIBRARY_MEMBERS = QLibraryMembers.libraryMembers;
    private static final QLibraryRoots LIBRARY_ROOTS = QLibraryRoots.libraryRoots;
    private static final QSearchDocuments SEARCH_DOCUMENTS = QSearchDocuments.searchDocuments;

    private final SQLQueryFactory queryFactory;

    AssetRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    List<LibraryRootRow> listLibraryRoots(UUID userId, boolean admin, UUID libraryId) {
        BooleanBuilder where = readableWhere(userId, admin, libraryId);

        return queryFactory
                .select(LIBRARY_ROOTS.libraryId, LIBRARY_ROOTS.normalizedPath)
                .from(LIBRARY_ROOTS)
                .join(LIBRARIES).on(LIBRARIES.id.eq(LIBRARY_ROOTS.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(where)
                .orderBy(LIBRARY_ROOTS.normalizedPath.asc())
                .fetch()
                .stream()
                .map(row -> new LibraryRootRow(
                        row.get(LIBRARY_ROOTS.libraryId),
                        row.get(LIBRARY_ROOTS.normalizedPath)
                ))
                .toList();
    }

    List<FolderRow> listFolders(UUID userId, boolean admin, UUID libraryId) {
        BooleanBuilder where = readableWhere(userId, admin, libraryId)
                .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING));

        return queryFactory
                .select(
                        ASSET_FILES.libraryId,
                        LIBRARIES.name,
                        ASSET_FILES.normalizedPath,
                        ASSET_FILES.assetId
                )
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(where)
                .orderBy(LIBRARIES.name.lower().asc(), ASSET_FILES.normalizedPath.asc())
                .fetch()
                .stream()
                .map(row -> new FolderRow(
                        row.get(ASSET_FILES.libraryId),
                        row.get(LIBRARIES.name),
                        folderPath(row.get(ASSET_FILES.normalizedPath)),
                        row.get(ASSET_FILES.assetId)
                ))
                .toList();
    }

    BrowseRows browse(UUID userId, boolean admin, AssetSearchCriteria criteria) {
        BooleanBuilder where = assetSearchWhere(userId, admin, criteria);

        Long count = queryFactory
                .select(ASSETS.id.countDistinct())
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .leftJoin(SEARCH_DOCUMENTS).on(SEARCH_DOCUMENTS.assetId.eq(ASSETS.id))
                .where(where)
                .fetchOne();
        int totalCount = count == null ? 0 : Math.toIntExact(count);

        if (totalCount == 0) {
            return new BrowseRows(List.of(), 0);
        }

        List<UUID> assetIds = queryFactory
                .select(ASSETS.id)
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .leftJoin(SEARCH_DOCUMENTS).on(SEARCH_DOCUMENTS.assetId.eq(ASSETS.id))
                .where(where)
                .groupBy(ASSETS.id)
                .orderBy(ASSET_FILES.normalizedPath.min().asc(), ASSET_FILES.fileName.lower().min().asc(), ASSETS.id.asc())
                .offset((long) criteria.page() * criteria.pageSize())
                .limit(criteria.pageSize())
                .fetch();

        if (assetIds.isEmpty()) {
            return new BrowseRows(List.of(), totalCount);
        }

        List<AssetFileRow> rows = baseFileQuery()
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .leftJoin(SEARCH_DOCUMENTS).on(SEARCH_DOCUMENTS.assetId.eq(ASSETS.id))
                .where(where.and(ASSETS.id.in(assetIds)))
                .orderBy(ASSET_FILES.normalizedPath.asc(), ASSET_FILES.fileName.lower().asc(), ASSET_FILES.assetId.asc())
                .fetch()
                .stream()
                .map(this::toAssetFileRow)
                .toList();

        Map<UUID, AssetGroup> groups = new LinkedHashMap<>();
        for (AssetFileRow row : rows) {
            groups.computeIfAbsent(row.assetId(), ignored -> new AssetGroup(row)).add(row);
        }

        List<AssetSummaryRow> summaries = groups.values().stream()
                .map(AssetGroup::toSummary)
                .toList();
        return new BrowseRows(summaries, totalCount);
    }

    boolean canReadAssetInLibrary(UUID userId, boolean admin, UUID assetId, UUID libraryId) {
        Integer result = queryFactory.selectOne()
                .from(ASSET_FILES)
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ASSET_FILES.assetId.eq(assetId)
                        .and(ASSET_FILES.libraryId.eq(libraryId))
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, libraryId)))
                .fetchFirst();
        return result != null;
    }

    boolean canReadAsset(UUID userId, boolean admin, UUID assetId) {
        Integer result = queryFactory.selectOne()
                .from(ASSET_FILES)
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ASSET_FILES.assetId.eq(assetId)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, null)))
                .fetchFirst();
        return result != null;
    }

    BrowseRows browseAlbumAssets(UUID userId, boolean admin, UUID albumId, int page, int pageSize) {
        Map<UUID, UUID> assetContexts = new LinkedHashMap<>();
        queryFactory.select(ALBUM_ITEMS.assetId, ALBUM_ITEMS.sourceLibraryId)
                .from(ALBUM_ITEMS)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ALBUM_ITEMS.assetId)
                        .and(ASSET_FILES.libraryId.eq(ALBUM_ITEMS.sourceLibraryId)))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ALBUM_ITEMS.albumId.eq(albumId)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, null)))
                .groupBy(ALBUM_ITEMS.assetId, ALBUM_ITEMS.sourceLibraryId, ALBUM_ITEMS.position)
                .orderBy(ALBUM_ITEMS.position.asc())
                .offset((long) page * pageSize)
                .limit(pageSize)
                .fetch()
                .forEach(row -> assetContexts.put(row.get(ALBUM_ITEMS.assetId), row.get(ALBUM_ITEMS.sourceLibraryId)));
        Long count = queryFactory.select(ALBUM_ITEMS.assetId.countDistinct())
                .from(ALBUM_ITEMS)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ALBUM_ITEMS.assetId)
                        .and(ASSET_FILES.libraryId.eq(ALBUM_ITEMS.sourceLibraryId)))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ALBUM_ITEMS.albumId.eq(albumId)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, null)))
                .fetchOne();
        return browseByIds(userId, admin, assetContexts, count);
    }

    BrowseRows browseTagAssets(UUID userId, boolean admin, UUID tagId, int page, int pageSize) {
        Map<UUID, UUID> assetContexts = new LinkedHashMap<>();
        queryFactory.select(ASSET_TAGS.assetId, ASSET_TAGS.sourceLibraryId)
                .from(ASSET_TAGS)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ASSET_TAGS.assetId)
                        .and(ASSET_FILES.libraryId.eq(ASSET_TAGS.sourceLibraryId)))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ASSET_TAGS.tagId.eq(tagId)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, null)))
                .groupBy(ASSET_TAGS.assetId, ASSET_TAGS.sourceLibraryId)
                .orderBy(ASSET_TAGS.assetId.asc())
                .offset((long) page * pageSize)
                .limit(pageSize)
                .fetch()
                .forEach(row -> assetContexts.put(row.get(ASSET_TAGS.assetId), row.get(ASSET_TAGS.sourceLibraryId)));
        Long count = queryFactory.select(ASSET_TAGS.assetId.countDistinct())
                .from(ASSET_TAGS)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ASSET_TAGS.assetId)
                        .and(ASSET_FILES.libraryId.eq(ASSET_TAGS.sourceLibraryId)))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(ASSET_TAGS.tagId.eq(tagId)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(readableWhere(userId, admin, null)))
                .fetchOne();
        return browseByIds(userId, admin, assetContexts, count);
    }

    Optional<AssetDetailRow> findAsset(UUID userId, boolean admin, UUID assetId) {
        List<AssetFileRow> rows = baseFileQuery()
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .leftJoin(SEARCH_DOCUMENTS).on(SEARCH_DOCUMENTS.assetId.eq(ASSETS.id))
                .where(readableWhere(userId, admin, null).and(ASSETS.id.eq(assetId)))
                .orderBy(ASSET_FILES.status.asc(), ASSET_FILES.normalizedPath.asc())
                .fetch()
                .stream()
                .map(this::toAssetFileRow)
                .toList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(AssetDetailRow.from(rows));
    }

    List<MetadataCandidateRow> listMetadataCandidates(int limit) {
        return queryFactory
                .selectDistinct(
                        ASSETS.id,
                        ASSET_FILES.id,
                        ASSET_FILES.path,
                        ASSET_FILES.normalizedPath,
                        ASSET_FILES.fileName,
                        ASSET_FILES.modifiedAt,
                        ASSETS.mediaType,
                        ASSET_FILES.lastObservedAt
                )
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .where(ASSET_FILES.status.eq(FILE_STATUS_ACTIVE))
                .orderBy(ASSET_FILES.lastObservedAt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new MetadataCandidateRow(
                        row.get(ASSETS.id),
                        row.get(ASSET_FILES.id),
                        row.get(ASSET_FILES.path),
                        row.get(ASSET_FILES.normalizedPath),
                        row.get(ASSET_FILES.fileName),
                        row.get(ASSET_FILES.modifiedAt),
                        row.get(ASSETS.mediaType)
                ))
                .toList();
    }

    List<UUID> listConfirmedAssetIds() {
        return queryFactory
                .select(ASSETS.id)
                .from(ASSETS)
                .where(ASSETS.contentHash.isNotNull(), ASSETS.contentHash.startsWith("provisional:").not())
                .fetch();
    }

    void upsertMetadata(MetadataUpdate update) {
        long updated = queryFactory.update(ASSET_METADATA)
                .set(ASSET_METADATA.capturedAt, update.capturedAt())
                .set(ASSET_METADATA.width, update.width())
                .set(ASSET_METADATA.height, update.height())
                .set(ASSET_METADATA.orientation, update.orientation())
                .set(ASSET_METADATA.fileExtension, update.fileExtension())
                .set(ASSET_METADATA.mimeType, update.mimeType())
                .set(ASSET_METADATA.cameraMake, update.cameraMake())
                .set(ASSET_METADATA.cameraModel, update.cameraModel())
                .set(ASSET_METADATA.sourceVersion, update.sourceVersion())
                .set(ASSET_METADATA.extractionStatus, update.extractionStatus())
                .set(ASSET_METADATA.extractedAt, update.extractedAt())
                .set(ASSET_METADATA.errorMessage, update.errorMessage())
                .where(ASSET_METADATA.assetId.eq(update.assetId()))
                .execute();

        if (updated == 0) {
            queryFactory.insert(ASSET_METADATA)
                    .set(ASSET_METADATA.assetId, update.assetId())
                    .set(ASSET_METADATA.capturedAt, update.capturedAt())
                    .set(ASSET_METADATA.width, update.width())
                    .set(ASSET_METADATA.height, update.height())
                    .set(ASSET_METADATA.orientation, update.orientation())
                    .set(ASSET_METADATA.fileExtension, update.fileExtension())
                    .set(ASSET_METADATA.mimeType, update.mimeType())
                    .set(ASSET_METADATA.cameraMake, update.cameraMake())
                    .set(ASSET_METADATA.cameraModel, update.cameraModel())
                    .set(ASSET_METADATA.sourceVersion, update.sourceVersion())
                    .set(ASSET_METADATA.extractionStatus, update.extractionStatus())
                    .set(ASSET_METADATA.extractedAt, update.extractedAt())
                    .set(ASSET_METADATA.errorMessage, update.errorMessage())
                    .execute();
        }
    }

    void upsertSearchDocument(UUID assetId, String searchableText, OffsetDateTime now) {
        long updated = queryFactory.update(SEARCH_DOCUMENTS)
                .set(SEARCH_DOCUMENTS.searchableText, searchableText)
                .set(SEARCH_DOCUMENTS.updatedAt, now)
                .where(SEARCH_DOCUMENTS.assetId.eq(assetId))
                .execute();

        if (updated == 0) {
            queryFactory.insert(SEARCH_DOCUMENTS)
                    .set(SEARCH_DOCUMENTS.assetId, assetId)
                    .set(SEARCH_DOCUMENTS.searchableText, searchableText)
                    .set(SEARCH_DOCUMENTS.updatedAt, now)
                    .execute();
        }
    }

    String searchableTextForAsset(UUID assetId) {
        List<String> parts = queryFactory
                .select(ASSET_FILES.fileName, ASSET_FILES.normalizedPath, ASSETS.mediaType, ASSET_METADATA.fileExtension, ASSET_METADATA.mimeType)
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .where(ASSET_FILES.assetId.eq(assetId))
                .fetch()
                .stream()
                .flatMap(row -> List.of(
                        row.get(ASSET_FILES.fileName),
                        row.get(ASSET_FILES.normalizedPath),
                        row.get(ASSETS.mediaType),
                        row.get(ASSET_METADATA.fileExtension),
                        row.get(ASSET_METADATA.mimeType)
                ).stream())
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return String.join(" ", parts);
    }

    private SQLQuery<Tuple> baseFileQuery() {
        return queryFactory
                .select(
                        ASSETS.id,
                        ASSETS.contentHash,
                        ASSETS.mediaType,
                        ASSETS.availableFileCount,
                        ASSETS.lastObservedAt,
                        ASSET_FILES.id,
                        ASSET_FILES.libraryId,
                        ASSET_FILES.path,
                        ASSET_FILES.normalizedPath,
                        ASSET_FILES.fileName,
                        ASSET_FILES.sizeBytes,
                        ASSET_FILES.modifiedAt,
                        ASSET_FILES.status,
                        LIBRARIES.name,
                        ASSET_METADATA.capturedAt,
                        ASSET_METADATA.width,
                        ASSET_METADATA.height,
                        ASSET_METADATA.fileExtension,
                        ASSET_METADATA.mimeType,
                        ASSET_METADATA.extractionStatus,
                        ASSET_METADATA.extractedAt,
                        ASSET_METADATA.errorMessage
                )
                .from(ASSET_FILES)
                .join(ASSETS).on(ASSETS.id.eq(ASSET_FILES.assetId))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id));
    }

    private BooleanBuilder readableWhere(UUID userId, boolean admin, UUID libraryId) {
        BooleanBuilder where = new BooleanBuilder()
                .and(LIBRARIES.status.eq(STATUS_ACTIVE));
        if (libraryId != null) {
            where.and(LIBRARIES.id.eq(libraryId));
        }
        if (!admin) {
            where.and(LIBRARY_MEMBERS.userId.eq(userId));
        }
        return where;
    }

    private BooleanBuilder assetSearchWhere(UUID userId, boolean admin, AssetSearchCriteria criteria) {
        BooleanBuilder where = readableWhere(userId, admin, criteria.libraryId())
                .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING));

        if (criteria.folder() != null && !criteria.folder().isBlank()) {
            String normalizedFolder = criteria.folder();
            if (criteria.includeDescendants()) {
                where.and(ASSET_FILES.normalizedPath.eq(normalizedFolder)
                        .or(ASSET_FILES.normalizedPath.startsWith(normalizedFolder + "/")));
            } else {
                List<FolderRow> folders = listFolders(userId, admin, criteria.libraryId()).stream()
                        .filter(folder -> folder.folderPath().equals(normalizedFolder))
                        .toList();
                if (folders.isEmpty()) {
                    where.and(ASSETS.id.isNull());
                } else {
                    where.and(ASSET_FILES.normalizedPath.startsWith(normalizedFolder + "/"));
                }
            }
        }

        if (criteria.availability() != null && !criteria.availability().isBlank()) {
            if (AVAILABILITY_MISSING.equals(criteria.availability())) {
                where.and(ASSETS.availableFileCount.eq(0));
            }
            if (AVAILABILITY_AVAILABLE.equals(criteria.availability())) {
                where.and(ASSETS.availableFileCount.gt(0));
            }
        }

        if (criteria.fileType() != null && !criteria.fileType().isBlank()) {
            where.and(ASSETS.mediaType.lower().startsWith(criteria.fileType().toLowerCase(Locale.ROOT)));
        }

        if (Boolean.TRUE.equals(criteria.duplicatesOnly())) {
            where.and(ASSETS.availableFileCount.gt(1));
        }

        if (criteria.query() != null && !criteria.query().isBlank()) {
            String query = criteria.query().trim().toLowerCase(Locale.ROOT);
            where.and(ASSET_FILES.fileName.lower().contains(query)
                    .or(ASSET_FILES.normalizedPath.lower().contains(query))
                    .or(ASSETS.mediaType.lower().contains(query))
                    .or(ASSET_METADATA.fileExtension.lower().contains(query))
                    .or(ASSET_METADATA.mimeType.lower().contains(query))
                    .or(SEARCH_DOCUMENTS.searchableText.lower().contains(query)));
        }

        if (criteria.tagIds() != null && !criteria.tagIds().isEmpty()) {
            for (UUID tagId : criteria.tagIds()) {
                QAssetTags matchingTags = new QAssetTags("matching_tags_" + tagId);
                QTags matchingTagDefinitions = new QTags("matching_tag_definitions_" + tagId);
                where.and(com.querydsl.sql.SQLExpressions.selectOne()
                        .from(matchingTags)
                        .join(matchingTagDefinitions).on(matchingTagDefinitions.id.eq(matchingTags.tagId))
                        .where(matchingTags.assetId.eq(ASSETS.id)
                                .and(matchingTags.tagId.eq(tagId))
                                .and(matchingTagDefinitions.ownerUserId.eq(criteria.tagOwnerUserId())))
                        .exists());
            }
        }

        return where;
    }

    private BrowseRows browseByIds(UUID userId, boolean admin, List<UUID> assetIds, Long count) {
        Map<UUID, UUID> assetContexts = new LinkedHashMap<>();
        for (UUID assetId : assetIds) {
            assetContexts.put(assetId, null);
        }
        return browseByIds(userId, admin, assetContexts, count);
    }

    private BrowseRows browseByIds(UUID userId, boolean admin, Map<UUID, UUID> assetContexts, Long count) {
        int totalCount = count == null ? 0 : Math.toIntExact(count);
        if (assetContexts.isEmpty()) {
            return new BrowseRows(List.of(), totalCount);
        }
        List<UUID> assetIds = List.copyOf(assetContexts.keySet());
        List<AssetFileRow> rows = baseFileQuery()
                .leftJoin(ASSET_METADATA).on(ASSET_METADATA.assetId.eq(ASSETS.id))
                .where(readableWhere(userId, admin, null)
                        .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING))
                        .and(ASSETS.id.in(assetIds)))
                .orderBy(ASSET_FILES.status.asc(), ASSET_FILES.normalizedPath.asc())
                .fetch().stream().map(this::toAssetFileRow).toList();
        Map<UUID, AssetGroup> groups = new LinkedHashMap<>();
        for (AssetFileRow row : rows) {
            groups.computeIfAbsent(row.assetId(), ignored -> new AssetGroup(row)).add(row);
        }
        return new BrowseRows(assetIds.stream()
                .map(groups::get)
                .filter(java.util.Objects::nonNull)
                .map(group -> group.toSummary(assetContexts.get(group.assetId())))
                .toList(), totalCount);
    }

    private AssetFileRow toAssetFileRow(Tuple row) {
        return new AssetFileRow(
                row.get(ASSETS.id),
                row.get(ASSETS.contentHash),
                row.get(ASSETS.mediaType),
                value(row.get(ASSETS.availableFileCount)),
                row.get(ASSETS.lastObservedAt),
                row.get(ASSET_FILES.id),
                row.get(ASSET_FILES.libraryId),
                row.get(LIBRARIES.name),
                row.get(ASSET_FILES.path),
                row.get(ASSET_FILES.normalizedPath),
                row.get(ASSET_FILES.fileName),
                row.get(ASSET_FILES.sizeBytes),
                row.get(ASSET_FILES.modifiedAt),
                row.get(ASSET_FILES.status),
                row.get(ASSET_METADATA.capturedAt),
                row.get(ASSET_METADATA.width),
                row.get(ASSET_METADATA.height),
                row.get(ASSET_METADATA.fileExtension),
                row.get(ASSET_METADATA.mimeType),
                row.get(ASSET_METADATA.extractionStatus),
                row.get(ASSET_METADATA.extractedAt),
                row.get(ASSET_METADATA.errorMessage)
        );
    }

    private static String folderPath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return normalizedPath;
        }
        return normalizedPath.substring(0, lastSlash);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean isImageMedia(String mimeType, String mediaType) {
        if (mimeType != null && mimeType.startsWith(IMAGE_MIME_PREFIX)) {
            return true;
        }
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("image") || normalized.startsWith(IMAGE_MIME_PREFIX);
    }

    record AssetSearchCriteria(
            UUID libraryId,
            String folder,
            boolean includeDescendants,
            String query,
            String availability,
            String fileType,
            Boolean duplicatesOnly,
            List<UUID> tagIds,
            UUID tagOwnerUserId,
            int page,
            int pageSize
    ) {
    }

    record BrowseRows(List<AssetSummaryRow> assets, int totalCount) {
    }

    record FolderRow(UUID libraryId, String libraryName, String folderPath, UUID assetId) {
    }

    record LibraryRootRow(UUID libraryId, String normalizedPath) {
    }

    record AssetFileRow(
            UUID assetId,
            String contentHash,
            String mediaType,
            int availableFileCount,
            OffsetDateTime observedAt,
            UUID fileId,
            UUID libraryId,
            String libraryName,
            String path,
            String normalizedPath,
            String fileName,
            long sizeBytes,
            OffsetDateTime modifiedAt,
            String status,
            OffsetDateTime capturedAt,
            Integer width,
            Integer height,
            String fileExtension,
            String mimeType,
            String extractionStatus,
            OffsetDateTime extractedAt,
            String errorMessage
    ) {
        String folderPath() {
            return AssetRepository.folderPath(normalizedPath);
        }
    }

    record AssetSummaryRow(
            UUID assetId,
            String fileName,
            String displayPath,
            String folderPath,
            UUID libraryId,
            String libraryName,
            String availability,
            String identityStatus,
            int duplicateCount,
            OffsetDateTime capturedAt,
            OffsetDateTime observedAt,
            String mediaType,
            String mimeType,
            Integer width,
            Integer height,
            String contentHash,
            boolean previewable
    ) {
    }

    record AssetDetailRow(
            UUID assetId,
            String contentHash,
            String identityStatus,
            String mediaType,
            String availability,
            int duplicateCount,
            AssetMetadataResponse metadata,
            List<AssetFileOccurrenceResponse> files
    ) {
        static AssetDetailRow from(List<AssetFileRow> rows) {
            AssetFileRow first = rows.get(0);
            List<AssetFileRow> active = rows.stream()
                    .filter(row -> FILE_STATUS_ACTIVE.equals(row.status()))
                    .toList();
            AssetFileRow metadataSource = active.isEmpty() ? first : active.get(0);
            return new AssetDetailRow(
                    first.assetId(),
                    first.contentHash(),
                    AssetIdentity.statusFor(first.contentHash()),
                    first.mediaType(),
                    first.availableFileCount() > 0 ? AVAILABILITY_AVAILABLE : AVAILABILITY_MISSING,
                    active.size(),
                    new AssetMetadataResponse(
                            metadataSource.capturedAt(),
                            metadataSource.width(),
                            metadataSource.height(),
                            metadataSource.fileExtension(),
                            metadataSource.mimeType(),
                            metadataSource.extractionStatus(),
                            metadataSource.extractedAt(),
                            metadataSource.errorMessage()
                    ),
                    rows.stream()
                            .map(row -> new AssetFileOccurrenceResponse(
                                    row.fileId(),
                                    row.libraryId(),
                                    row.libraryName(),
                                    row.path(),
                                    row.folderPath(),
                                    row.fileName(),
                                    row.sizeBytes(),
                                    row.modifiedAt(),
                                    row.status()
                            ))
                            .toList()
            );
        }
    }

    record MetadataCandidateRow(
            UUID assetId,
            UUID assetFileId,
            String path,
            String normalizedPath,
            String fileName,
            OffsetDateTime modifiedAt,
            String mediaType
    ) {
    }

    record MetadataUpdate(
            UUID assetId,
            OffsetDateTime capturedAt,
            Integer width,
            Integer height,
            Integer orientation,
            String fileExtension,
            String mimeType,
            String cameraMake,
            String cameraModel,
            String sourceVersion,
            String extractionStatus,
            OffsetDateTime extractedAt,
            String errorMessage
    ) {
    }

    private static final class AssetGroup {
        private final AssetFileRow first;
        private final List<AssetFileRow> files = new ArrayList<>();

        private AssetGroup(AssetFileRow first) {
            this.first = first;
        }

        private void add(AssetFileRow row) {
            files.add(row);
        }

        private AssetSummaryRow toSummary() {
            return toSummary(null);
        }

        private AssetSummaryRow toSummary(UUID preferredLibraryId) {
            List<AssetFileRow> active = files.stream()
                    .filter(row -> FILE_STATUS_ACTIVE.equals(row.status()))
                    .toList();
            AssetFileRow display = preferredFile(preferredLibraryId).orElseGet(() -> active.isEmpty() ? first : active.get(0));
            return new AssetSummaryRow(
                    first.assetId(),
                    display.fileName(),
                    display.path(),
                    display.folderPath(),
                    display.libraryId(),
                    display.libraryName(),
                    first.availableFileCount() > 0 ? AVAILABILITY_AVAILABLE : AVAILABILITY_MISSING,
                    AssetIdentity.statusFor(first.contentHash()),
                    active.size(),
                    display.capturedAt(),
                    first.observedAt(),
                    first.mediaType(),
                    display.mimeType(),
                    display.width(),
                    display.height(),
                    first.contentHash(),
                    isPreviewable(display)
            );
        }

        private UUID assetId() {
            return first.assetId();
        }

        private Optional<AssetFileRow> preferredFile(UUID preferredLibraryId) {
            if (preferredLibraryId == null) {
                return Optional.empty();
            }
            Optional<AssetFileRow> activePreferred = files.stream()
                    .filter(row -> preferredLibraryId.equals(row.libraryId()))
                    .filter(row -> FILE_STATUS_ACTIVE.equals(row.status()))
                    .findFirst();
            return activePreferred.isPresent()
                    ? activePreferred
                    : files.stream().filter(row -> preferredLibraryId.equals(row.libraryId())).findFirst();
        }

        private boolean isPreviewable(AssetFileRow row) {
            return FILE_STATUS_ACTIVE.equals(row.status()) && isImageMedia(row.mimeType(), row.mediaType());
        }
    }
}
