package com.pixierge.api.libraries;

import com.pixierge.api.db.QAssetFiles;
import com.pixierge.api.db.QGlobalExclusionPatterns;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryExclusionPatterns;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QLibraryRoots;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LibraryRepository {

    private static final QLibraries LIBRARIES = QLibraries.libraries;
    private static final QAssetFiles ASSET_FILES = QAssetFiles.assetFiles;
    private static final QGlobalExclusionPatterns GLOBAL_EXCLUSION_PATTERNS =
            QGlobalExclusionPatterns.globalExclusionPatterns;
    private static final QLibraryExclusionPatterns LIBRARY_EXCLUSION_PATTERNS =
            QLibraryExclusionPatterns.libraryExclusionPatterns;
    private static final QLibraryMembers LIBRARY_MEMBERS = QLibraryMembers.libraryMembers;
    private static final QLibraryRoots LIBRARY_ROOTS = QLibraryRoots.libraryRoots;
    private static final String OWNER_ROLE = "owner";
    private final SQLQueryFactory queryFactory;

    public LibraryRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Transactional(readOnly = true)
    public List<LibraryRecord> listLibraries() {
        List<Tuple> libraryRows = queryFactory
                .select(LIBRARIES.id, LIBRARIES.name, LIBRARIES.status, LIBRARIES.createdAt, LIBRARIES.updatedAt, LIBRARIES.archivedAt)
                .from(LIBRARIES)
                .orderBy(LIBRARIES.name.lower().asc())
                .fetch();

        List<UUID> libraryIds = libraryRows.stream()
                .map(row -> row.get(LIBRARIES.id))
                .toList();
        Map<UUID, List<LibraryRootRecord>> rootsByLibrary = rootsByLibrary(libraryIds);
        Map<UUID, List<LibraryExclusionPatternRecord>> patternsByLibrary = exclusionPatternsByLibrary(libraryIds);
        List<LibraryRecord> libraries = new ArrayList<>();

        for (Tuple row : libraryRows) {
            UUID libraryId = row.get(LIBRARIES.id);
            libraries.add(new LibraryRecord(
                    libraryId,
                    row.get(LIBRARIES.name),
                    row.get(LIBRARIES.status),
                    row.get(LIBRARIES.createdAt),
                    row.get(LIBRARIES.updatedAt),
                    row.get(LIBRARIES.archivedAt),
                    rootsByLibrary.getOrDefault(libraryId, List.of()),
                    patternsByLibrary.getOrDefault(libraryId, List.of())
            ));
        }

        return libraries;
    }

    @Transactional(readOnly = true)
    public Optional<LibraryRecord> findLibrary(UUID libraryId) {
        Tuple row = queryFactory
                .select(LIBRARIES.id, LIBRARIES.name, LIBRARIES.status, LIBRARIES.createdAt, LIBRARIES.updatedAt, LIBRARIES.archivedAt)
                .from(LIBRARIES)
                .where(LIBRARIES.id.eq(libraryId))
                .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(new LibraryRecord(
                row.get(LIBRARIES.id),
                row.get(LIBRARIES.name),
                row.get(LIBRARIES.status),
                row.get(LIBRARIES.createdAt),
                row.get(LIBRARIES.updatedAt),
                row.get(LIBRARIES.archivedAt),
                rootsByLibrary(List.of(libraryId)).getOrDefault(libraryId, List.of()),
                exclusionPatternsByLibrary(List.of(libraryId)).getOrDefault(libraryId, List.of())
        ));
    }

    public UUID createLibrary(String name, UUID creatorId) {
        UUID libraryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        queryFactory.insert(LIBRARIES)
                .set(LIBRARIES.id, libraryId)
                .set(LIBRARIES.name, name)
                .set(LIBRARIES.createdBy, creatorId)
                .set(LIBRARIES.status, "active")
                .set(LIBRARIES.createdAt, now)
                .set(LIBRARIES.updatedAt, now)
                .execute();

        queryFactory.insert(LIBRARY_MEMBERS)
                .set(LIBRARY_MEMBERS.libraryId, libraryId)
                .set(LIBRARY_MEMBERS.userId, creatorId)
                .set(LIBRARY_MEMBERS.memberRole, OWNER_ROLE)
                .set(LIBRARY_MEMBERS.createdAt, now)
                .execute();

        return libraryId;
    }

    @Transactional(readOnly = true)
    public List<GlobalExclusionPatternRecord> listGlobalExclusionPatterns() {
        return queryFactory
                .select(GLOBAL_EXCLUSION_PATTERNS.id, GLOBAL_EXCLUSION_PATTERNS.pattern, GLOBAL_EXCLUSION_PATTERNS.createdAt)
                .from(GLOBAL_EXCLUSION_PATTERNS)
                .orderBy(GLOBAL_EXCLUSION_PATTERNS.pattern.lower().asc())
                .fetch()
                .stream()
                .map(row -> new GlobalExclusionPatternRecord(
                        row.get(GLOBAL_EXCLUSION_PATTERNS.id),
                        row.get(GLOBAL_EXCLUSION_PATTERNS.pattern),
                        row.get(GLOBAL_EXCLUSION_PATTERNS.createdAt)
                ))
                .toList();
    }

    public UUID addGlobalExclusionPattern(String pattern) {
        UUID patternId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        queryFactory.insert(GLOBAL_EXCLUSION_PATTERNS)
                .set(GLOBAL_EXCLUSION_PATTERNS.id, patternId)
                .set(GLOBAL_EXCLUSION_PATTERNS.pattern, pattern)
                .set(GLOBAL_EXCLUSION_PATTERNS.createdAt, now)
                .execute();
        return patternId;
    }

    public boolean deleteGlobalExclusionPattern(UUID patternId) {
        return queryFactory.delete(GLOBAL_EXCLUSION_PATTERNS)
                .where(GLOBAL_EXCLUSION_PATTERNS.id.eq(patternId))
                .execute() > 0;
    }

    @Transactional(readOnly = true)
    public boolean globalExclusionPatternExists(String pattern) {
        Integer exists = queryFactory
                .selectOne()
                .from(GLOBAL_EXCLUSION_PATTERNS)
                .where(GLOBAL_EXCLUSION_PATTERNS.pattern.eq(pattern))
                .fetchFirst();
        return exists != null;
    }

    public UUID addRoot(UUID libraryId, String path, String normalizedPath) {
        UUID rootId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        queryFactory.insert(LIBRARY_ROOTS)
                .set(LIBRARY_ROOTS.id, rootId)
                .set(LIBRARY_ROOTS.libraryId, libraryId)
                .set(LIBRARY_ROOTS.path, path)
                .set(LIBRARY_ROOTS.normalizedPath, normalizedPath)
                .set(LIBRARY_ROOTS.createdAt, now)
                .set(LIBRARY_ROOTS.updatedAt, now)
                .execute();

        queryFactory.update(LIBRARIES)
                .set(LIBRARIES.updatedAt, now)
                .where(LIBRARIES.id.eq(libraryId))
                .execute();

        return rootId;
    }

    public boolean deleteRoot(UUID libraryId, UUID rootId) {
        long deleted = queryFactory.delete(LIBRARY_ROOTS)
                .where(LIBRARY_ROOTS.id.eq(rootId).and(LIBRARY_ROOTS.libraryId.eq(libraryId)))
                .execute();

        if (deleted > 0) {
            queryFactory.update(LIBRARIES)
                    .set(LIBRARIES.updatedAt, OffsetDateTime.now())
                    .where(LIBRARIES.id.eq(libraryId))
                    .execute();
        }

        return deleted > 0;
    }

    public boolean archiveLibrary(UUID libraryId) {
        OffsetDateTime now = OffsetDateTime.now();
        return queryFactory.update(LIBRARIES)
                .set(LIBRARIES.status, "archived")
                .set(LIBRARIES.archivedAt, now)
                .set(LIBRARIES.updatedAt, now)
                .where(LIBRARIES.id.eq(libraryId).and(LIBRARIES.status.ne("archived")))
                .execute() > 0;
    }

    public boolean restoreLibrary(UUID libraryId) {
        OffsetDateTime now = OffsetDateTime.now();
        return queryFactory.update(LIBRARIES)
                .set(LIBRARIES.status, "active")
                .setNull(LIBRARIES.archivedAt)
                .set(LIBRARIES.updatedAt, now)
                .where(LIBRARIES.id.eq(libraryId).and(LIBRARIES.status.eq("archived")))
                .execute() > 0;
    }

    public UUID addExclusionPattern(UUID libraryId, String pattern) {
        UUID patternId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        queryFactory.insert(LIBRARY_EXCLUSION_PATTERNS)
                .set(LIBRARY_EXCLUSION_PATTERNS.id, patternId)
                .set(LIBRARY_EXCLUSION_PATTERNS.libraryId, libraryId)
                .set(LIBRARY_EXCLUSION_PATTERNS.pattern, pattern)
                .set(LIBRARY_EXCLUSION_PATTERNS.createdAt, now)
                .execute();
        queryFactory.update(LIBRARIES)
                .set(LIBRARIES.updatedAt, now)
                .where(LIBRARIES.id.eq(libraryId))
                .execute();
        return patternId;
    }

    public boolean deleteExclusionPattern(UUID libraryId, UUID patternId) {
        long deleted = queryFactory.delete(LIBRARY_EXCLUSION_PATTERNS)
                .where(LIBRARY_EXCLUSION_PATTERNS.id.eq(patternId)
                        .and(LIBRARY_EXCLUSION_PATTERNS.libraryId.eq(libraryId)))
                .execute();

        if (deleted > 0) {
            queryFactory.update(LIBRARIES)
                    .set(LIBRARIES.updatedAt, OffsetDateTime.now())
                    .where(LIBRARIES.id.eq(libraryId))
                    .execute();
        }

        return deleted > 0;
    }

    @Transactional(readOnly = true)
    public boolean exclusionPatternExists(UUID libraryId, String pattern) {
        Integer exists = queryFactory
                .selectOne()
                .from(LIBRARY_EXCLUSION_PATTERNS)
                .where(LIBRARY_EXCLUSION_PATTERNS.libraryId.eq(libraryId)
                        .and(LIBRARY_EXCLUSION_PATTERNS.pattern.eq(pattern)))
                .fetchFirst();
        return exists != null;
    }

    @Transactional(readOnly = true)
    public boolean libraryNameExists(String name) {
        Integer exists = queryFactory
                .selectOne()
                .from(LIBRARIES)
                .where(LIBRARIES.name.lower().eq(name.toLowerCase(Locale.ROOT)))
                .fetchFirst();
        return exists != null;
    }

    @Transactional(readOnly = true)
    public boolean libraryNameExistsExcluding(String name, UUID excludeLibraryId) {
        Integer exists = queryFactory
                .selectOne()
                .from(LIBRARIES)
                .where(LIBRARIES.name.lower().eq(name.toLowerCase(Locale.ROOT))
                        .and(LIBRARIES.id.ne(excludeLibraryId)))
                .fetchFirst();
        return exists != null;
    }

    public boolean updateLibraryName(UUID libraryId, String name) {
        long updated = queryFactory.update(LIBRARIES)
                .set(LIBRARIES.name, name)
                .set(LIBRARIES.updatedAt, OffsetDateTime.now())
                .where(LIBRARIES.id.eq(libraryId))
                .execute();
        return updated > 0;
    }

    @Transactional(readOnly = true)
    public boolean rootPathExists(String normalizedPath) {
        Integer exists = queryFactory
                .selectOne()
                .from(LIBRARY_ROOTS)
                .where(LIBRARY_ROOTS.normalizedPath.eq(normalizedPath))
                .fetchFirst();
        return exists != null;
    }

    public void rewriteAssetFilePathPrefix(String oldPrefix, String newPrefix) {
        List<Tuple> rows = queryFactory
                .select(ASSET_FILES.id, ASSET_FILES.path, ASSET_FILES.normalizedPath)
                .from(ASSET_FILES)
                .where(ASSET_FILES.normalizedPath.eq(oldPrefix)
                        .or(ASSET_FILES.normalizedPath.startsWith(oldPrefix + "/")))
                .fetch();

        for (Tuple row : rows) {
            UUID fileId = row.get(ASSET_FILES.id);
            String path = rewritePrefix(row.get(ASSET_FILES.path), oldPrefix, newPrefix);
            String normalizedPath = rewritePrefix(row.get(ASSET_FILES.normalizedPath), oldPrefix, newPrefix);
            queryFactory.update(ASSET_FILES)
                    .set(ASSET_FILES.path, path)
                    .set(ASSET_FILES.normalizedPath, normalizedPath)
                    .where(ASSET_FILES.id.eq(fileId))
                    .execute();
        }
    }

    public void rewriteRootPathPrefix(String oldPrefix, String newPrefix) {
        List<Tuple> rows = queryFactory
                .select(LIBRARY_ROOTS.id, LIBRARY_ROOTS.path, LIBRARY_ROOTS.normalizedPath)
                .from(LIBRARY_ROOTS)
                .where(LIBRARY_ROOTS.normalizedPath.eq(oldPrefix)
                        .or(LIBRARY_ROOTS.normalizedPath.startsWith(oldPrefix + "/")))
                .fetch();

        for (Tuple row : rows) {
            UUID rootId = row.get(LIBRARY_ROOTS.id);
            String path = rewritePrefix(row.get(LIBRARY_ROOTS.path), oldPrefix, newPrefix);
            String normalizedPath = rewritePrefix(row.get(LIBRARY_ROOTS.normalizedPath), oldPrefix, newPrefix);
            queryFactory.update(LIBRARY_ROOTS)
                    .set(LIBRARY_ROOTS.path, path)
                    .set(LIBRARY_ROOTS.normalizedPath, normalizedPath)
                    .where(LIBRARY_ROOTS.id.eq(rootId))
                    .execute();
        }
    }

    private static String rewritePrefix(String value, String oldPrefix, String newPrefix) {
        if (value == null) {
            return null;
        }
        if (value.equals(oldPrefix)) {
            return newPrefix;
        }
        if (value.startsWith(oldPrefix + "/")) {
            return newPrefix + value.substring(oldPrefix.length());
        }
        return value;
    }

    boolean isDuplicateKey(DataIntegrityViolationException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("duplicate key");
    }

    private Map<UUID, List<LibraryRootRecord>> rootsByLibrary(List<UUID> libraryIds) {
        if (libraryIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = queryFactory
                .select(
                        LIBRARY_ROOTS.id,
                        LIBRARY_ROOTS.libraryId,
                        LIBRARY_ROOTS.path,
                        LIBRARY_ROOTS.normalizedPath,
                        LIBRARY_ROOTS.createdAt
                )
                .from(LIBRARY_ROOTS)
                .where(LIBRARY_ROOTS.libraryId.in(libraryIds))
                .orderBy(LIBRARY_ROOTS.path.lower().asc())
                .fetch();

        Map<UUID, List<LibraryRootRecord>> rootsByLibrary = new LinkedHashMap<>();
        for (Tuple row : rows) {
            rootsByLibrary.computeIfAbsent(row.get(LIBRARY_ROOTS.libraryId), ignored -> new ArrayList<>())
                    .add(new LibraryRootRecord(
                            row.get(LIBRARY_ROOTS.id),
                            row.get(LIBRARY_ROOTS.libraryId),
                            row.get(LIBRARY_ROOTS.path),
                            row.get(LIBRARY_ROOTS.normalizedPath),
                            row.get(LIBRARY_ROOTS.createdAt)
                    ));
        }
        return rootsByLibrary;
    }

    private Map<UUID, List<LibraryExclusionPatternRecord>> exclusionPatternsByLibrary(List<UUID> libraryIds) {
        if (libraryIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = queryFactory
                .select(
                        LIBRARY_EXCLUSION_PATTERNS.id,
                        LIBRARY_EXCLUSION_PATTERNS.libraryId,
                        LIBRARY_EXCLUSION_PATTERNS.pattern,
                        LIBRARY_EXCLUSION_PATTERNS.createdAt
                )
                .from(LIBRARY_EXCLUSION_PATTERNS)
                .where(LIBRARY_EXCLUSION_PATTERNS.libraryId.in(libraryIds))
                .orderBy(LIBRARY_EXCLUSION_PATTERNS.pattern.lower().asc())
                .fetch();

        Map<UUID, List<LibraryExclusionPatternRecord>> patternsByLibrary = new LinkedHashMap<>();
        for (Tuple row : rows) {
            patternsByLibrary.computeIfAbsent(row.get(LIBRARY_EXCLUSION_PATTERNS.libraryId), ignored -> new ArrayList<>())
                    .add(new LibraryExclusionPatternRecord(
                            row.get(LIBRARY_EXCLUSION_PATTERNS.id),
                            row.get(LIBRARY_EXCLUSION_PATTERNS.libraryId),
                            row.get(LIBRARY_EXCLUSION_PATTERNS.pattern),
                            row.get(LIBRARY_EXCLUSION_PATTERNS.createdAt)
                    ));
        }
        return patternsByLibrary;
    }

    public record LibraryRecord(
            UUID id,
            String name,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime archivedAt,
            List<LibraryRootRecord> roots,
            List<LibraryExclusionPatternRecord> exclusionPatterns
    ) {
    }

    public record LibraryRootRecord(
            UUID id,
            UUID libraryId,
            String path,
            String normalizedPath,
            OffsetDateTime createdAt
    ) {
    }

    public record LibraryExclusionPatternRecord(
            UUID id,
            UUID libraryId,
            String pattern,
            OffsetDateTime createdAt
    ) {
    }

    public record GlobalExclusionPatternRecord(
            UUID id,
            String pattern,
            OffsetDateTime createdAt
    ) {
    }
}
