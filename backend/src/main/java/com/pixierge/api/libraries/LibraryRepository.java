package com.pixierge.api.libraries;

import com.pixierge.api.db.QLibraries;
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
                .select(LIBRARIES.id, LIBRARIES.name, LIBRARIES.createdAt, LIBRARIES.updatedAt)
                .from(LIBRARIES)
                .orderBy(LIBRARIES.name.lower().asc())
                .fetch();

        List<UUID> libraryIds = libraryRows.stream()
                .map(row -> row.get(LIBRARIES.id))
                .toList();
        Map<UUID, List<LibraryRootRecord>> rootsByLibrary = rootsByLibrary(libraryIds);
        List<LibraryRecord> libraries = new ArrayList<>();

        for (Tuple row : libraryRows) {
            UUID libraryId = row.get(LIBRARIES.id);
            libraries.add(new LibraryRecord(
                    libraryId,
                    row.get(LIBRARIES.name),
                    row.get(LIBRARIES.createdAt),
                    row.get(LIBRARIES.updatedAt),
                    rootsByLibrary.getOrDefault(libraryId, List.of())
            ));
        }

        return libraries;
    }

    @Transactional(readOnly = true)
    public Optional<LibraryRecord> findLibrary(UUID libraryId) {
        Tuple row = queryFactory
                .select(LIBRARIES.id, LIBRARIES.name, LIBRARIES.createdAt, LIBRARIES.updatedAt)
                .from(LIBRARIES)
                .where(LIBRARIES.id.eq(libraryId))
                .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(new LibraryRecord(
                row.get(LIBRARIES.id),
                row.get(LIBRARIES.name),
                row.get(LIBRARIES.createdAt),
                row.get(LIBRARIES.updatedAt),
                rootsByLibrary(List.of(libraryId)).getOrDefault(libraryId, List.of())
        ));
    }

    public UUID createLibrary(String name, UUID creatorId) {
        UUID libraryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        queryFactory.insert(LIBRARIES)
                .set(LIBRARIES.id, libraryId)
                .set(LIBRARIES.name, name)
                .set(LIBRARIES.createdBy, creatorId)
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
    public boolean rootPathExists(String normalizedPath) {
        Integer exists = queryFactory
                .selectOne()
                .from(LIBRARY_ROOTS)
                .where(LIBRARY_ROOTS.normalizedPath.eq(normalizedPath))
                .fetchFirst();
        return exists != null;
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

    public record LibraryRecord(
            UUID id,
            String name,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<LibraryRootRecord> roots
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
}
