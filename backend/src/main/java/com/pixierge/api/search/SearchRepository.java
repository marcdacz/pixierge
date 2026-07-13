package com.pixierge.api.search;

import com.pixierge.api.albums.AlbumKind;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QAssetMetadata;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QTags;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_ACTIVE;
import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_MISSING;
import static com.pixierge.api.libraries.LibraryConstants.STATUS_ACTIVE;

@Repository
class SearchRepository {
    private static final QAlbums ALBUMS = QAlbums.albums;
    private static final QAssetFiles ASSET_FILES = QAssetFiles.assetFiles;
    private static final QAssetMetadata ASSET_METADATA = QAssetMetadata.assetMetadata;
    private static final QLibraries LIBRARIES = QLibraries.libraries;
    private static final QLibraryMembers LIBRARY_MEMBERS = QLibraryMembers.libraryMembers;
    private static final QTags TAGS = QTags.tags;

    private final SQLQueryFactory queryFactory;

    SearchRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    List<SearchSuggestionResponse> suggest(
            SearchField field,
            String partial,
            int limit,
            UUID userId,
            boolean admin
    ) {
        String rawPartial = partial == null ? "" : partial.trim();
        String normalized = rawPartial.toLowerCase(Locale.ROOT);
        return switch (field) {
            case LIBRARY -> commaAwareNamedSuggestions(
                    queryFactory.select(LIBRARIES.name)
                            .from(LIBRARIES)
                            .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                            .where(librarySuggestionWhere(userId, admin, lastCommaPartial(normalized)))
                            .groupBy(LIBRARIES.name).orderBy(LIBRARIES.name.lower().asc()).limit(limit).fetch(),
                    rawPartial);
            case FOLDER -> folderSuggestions(normalized, limit, userId, admin);
            case ALBUM -> commaAwareNamedSuggestions(
                    namedSuggestionValues(
                            ALBUMS.name,
                            ALBUMS.ownerUserId.eq(userId).and(ALBUMS.kind.eq(AlbumKind.USER)),
                            lastCommaPartial(normalized),
                            limit),
                    rawPartial);
            case TAG -> commaAwareNamedSuggestions(
                    namedSuggestionValues(TAGS.name, TAGS.ownerUserId.eq(userId), lastCommaPartial(normalized), limit),
                    rawPartial);
            case IS -> enumSuggestions(List.of("available", "missing", "duplicate", "starred"), normalized, limit);
            case EXTENSION -> {
                int lastComma = normalized.lastIndexOf(',');
                String completedPrefix = lastComma >= 0 ? normalized.substring(0, lastComma + 1) : "";
                String lastPartial = lastComma >= 0 ? normalized.substring(lastComma + 1).trim() : normalized;
                String extensionPartial = lastPartial.startsWith(".") ? lastPartial.substring(1) : lastPartial;
                yield distinctSuggestions(ASSET_METADATA.fileExtension, extensionPartial, limit).stream()
                        .map(suggestion -> {
                            String dotted = suggestion.value().startsWith(".")
                                    ? suggestion.value()
                                    : "." + suggestion.value();
                            String value = completedPrefix + dotted;
                            return new SearchSuggestionResponse(value, value);
                        })
                        .toList();
            }
            case CAMERA -> cameraSuggestions(normalized, limit, userId, admin);
            case AFTER, BEFORE, ON -> List.of();
        };
    }

    private List<String> namedSuggestionValues(StringPath name, Predicate owner, String partial, int limit) {
        BooleanBuilder where = new BooleanBuilder(owner);
        if (!partial.isEmpty()) {
            where.and(name.lower().contains(partial));
        }
        return queryFactory.select(name).from(name.getRoot()).where(where)
                .orderBy(name.lower().asc()).limit(limit).fetch();
    }

    private List<SearchSuggestionResponse> commaAwareNamedSuggestions(List<String> matches, String rawPartial) {
        int lastComma = rawPartial.lastIndexOf(',');
        String completedPrefix = lastComma >= 0 ? rawPartial.substring(0, lastComma + 1) : "";
        return matches.stream()
                .map(value -> {
                    String quoted = quoteIfNeeded(value);
                    String suggestionValue = completedPrefix.isEmpty() ? quoted : completedPrefix + quoted;
                    return new SearchSuggestionResponse(suggestionValue, value);
                })
                .toList();
    }

    private static String lastCommaPartial(String normalized) {
        int lastComma = normalized.lastIndexOf(',');
        return lastComma >= 0 ? normalized.substring(lastComma + 1).trim() : normalized;
    }

    private List<SearchSuggestionResponse> folderSuggestions(String partial, int limit, UUID userId, boolean admin) {
        List<String> paths = queryFactory.select(ASSET_FILES.normalizedPath)
                .from(ASSET_FILES)
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(LIBRARIES.id))
                .where(folderSuggestionWhere(userId, admin, partial))
                .orderBy(ASSET_FILES.normalizedPath.asc()).limit((long) limit * 4).fetch();
        Set<String> folders = new LinkedHashSet<>();
        for (String path : paths) {
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                folders.add(path.substring(0, slash));
            }
            if (folders.size() == limit) {
                break;
            }
        }
        return folders.stream().map(value -> new SearchSuggestionResponse(quoteIfNeeded(value), value)).toList();
    }

    private List<SearchSuggestionResponse> distinctSuggestions(StringPath path, String partial, int limit) {
        BooleanBuilder where = new BooleanBuilder(path.isNotNull());
        if (!partial.isEmpty()) {
            where.and(path.lower().contains(partial));
        }
        return queryFactory.select(path).from(path.getRoot()).where(where)
                .groupBy(path).orderBy(path.lower().asc()).limit(limit).fetch().stream()
                .map(value -> new SearchSuggestionResponse(value, value)).toList();
    }

    private List<SearchSuggestionResponse> cameraSuggestions(String partial, int limit, UUID userId, boolean admin) {
        List<String> values = queryFactory.select(ASSET_METADATA.cameraMake, ASSET_METADATA.cameraModel)
                .from(ASSET_METADATA)
                .join(ASSET_FILES).on(ASSET_FILES.assetId.eq(ASSET_METADATA.assetId))
                .join(LIBRARIES).on(LIBRARIES.id.eq(ASSET_FILES.libraryId))
                .leftJoin(LIBRARY_MEMBERS).on(LIBRARY_MEMBERS.libraryId.eq(ASSET_FILES.libraryId))
                .where(ASSET_METADATA.cameraMake.isNotNull().or(ASSET_METADATA.cameraModel.isNotNull())
                        .and(readableAssetSuggestionWhere(userId, admin)))
                .limit((long) limit * 4).fetch().stream()
                .map(row -> String.join(" ", List.of(
                        row.get(ASSET_METADATA.cameraMake) == null ? "" : row.get(ASSET_METADATA.cameraMake),
                        row.get(ASSET_METADATA.cameraModel) == null ? "" : row.get(ASSET_METADATA.cameraModel)
                )).trim())
                .filter(value -> partial.isEmpty() || value.toLowerCase(Locale.ROOT).contains(partial))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(limit).toList();
        return values.stream().map(value -> new SearchSuggestionResponse(quoteIfNeeded(value), value)).toList();
    }

    private List<SearchSuggestionResponse> enumSuggestions(List<String> values, String partial, int limit) {
        return values.stream().filter(value -> value.startsWith(partial)).limit(limit)
                .map(value -> new SearchSuggestionResponse(value, value)).toList();
    }

    private BooleanBuilder readableLibraries(UUID userId, boolean admin) {
        BooleanBuilder where = new BooleanBuilder(LIBRARIES.status.eq(STATUS_ACTIVE));
        if (!admin) {
            where.and(LIBRARY_MEMBERS.userId.eq(userId));
        }
        return where;
    }

    private BooleanBuilder librarySuggestionWhere(UUID userId, boolean admin, String partial) {
        BooleanBuilder where = readableLibraries(userId, admin);
        if (!partial.isEmpty()) {
            where.and(LIBRARIES.name.lower().contains(partial));
        }
        return where;
    }

    private BooleanBuilder folderSuggestionWhere(UUID userId, boolean admin, String partial) {
        BooleanBuilder where = readableLibraries(userId, admin);
        if (!partial.isEmpty()) {
            where.and(ASSET_FILES.normalizedPath.lower().contains(partial));
        }
        return where;
    }

    private BooleanBuilder readableAssetSuggestionWhere(UUID userId, boolean admin) {
        BooleanBuilder where = new BooleanBuilder(LIBRARIES.status.eq(STATUS_ACTIVE))
                .and(ASSET_FILES.status.in(FILE_STATUS_ACTIVE, FILE_STATUS_MISSING));
        if (!admin) {
            where.and(LIBRARY_MEMBERS.userId.eq(userId));
        }
        return where;
    }

    static String quoteIfNeeded(String value) {
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
