package com.pixierge.api.libraries;

import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LibraryServiceTest {

    private static final UUID LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    private Path tempDir;

    @Test
    void createLibraryNormalizesWhitespaceBeforePersisting() {
        UUID creatorId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        StubLibraryRepository repository = new StubLibraryRepository();
        LibraryService service = new LibraryService(repository);

        LibraryResponse response = service.createLibrary(new CreateLibraryRequest("  Family   Photos  "), creatorId);

        assertThat(response.name()).isEqualTo("Family Photos");
        assertThat(repository.createdLibraryName).isEqualTo("Family Photos");
        assertThat(repository.createdLibraryOwner).isEqualTo(creatorId);
    }

    @Test
    void createLibraryMapsDuplicateKeyFailuresToConflict() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.createLibraryFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.createLibrary(new CreateLibraryRequest("Family Photos"), UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createLibraryRejectsBlankAndOverlongNamesBeforeRepositoryAccess() {
        StubLibraryRepository repository = new StubLibraryRepository();
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.createLibrary(new CreateLibraryRequest("   "), UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createLibrary(new CreateLibraryRequest("x".repeat(81)), UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(repository.createdLibraryName).isNull();
    }

    @Test
    void updateLibraryNormalizesNameAndReturnsUpdatedRecord() {
        StubLibraryRepository repository = new StubLibraryRepository();
        LibraryService service = new LibraryService(repository);

        LibraryResponse response = service.updateLibrary(LIBRARY_ID, new UpdateLibraryRequest("  Travel   Photos  "));

        assertThat(response.name()).isEqualTo("Travel Photos");
        assertThat(repository.updatedLibraryId).isEqualTo(LIBRARY_ID);
        assertThat(repository.updatedLibraryName).isEqualTo("Travel Photos");
    }

    @Test
    void updateLibraryMapsDuplicateNameChecksAndWriteRacesToConflict() {
        StubLibraryRepository existingRepository = new StubLibraryRepository();
        existingRepository.libraryNameExistsExcluding = true;
        LibraryService existingService = new LibraryService(existingRepository);

        assertThatThrownBy(() -> existingService.updateLibrary(LIBRARY_ID, new UpdateLibraryRequest("Travel Photos")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        StubLibraryRepository raceRepository = new StubLibraryRepository();
        raceRepository.updateLibraryFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        LibraryService raceService = new LibraryService(raceRepository);

        assertThatThrownBy(() -> raceService.updateLibrary(LIBRARY_ID, new UpdateLibraryRequest("Travel Photos")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateLibraryReturnsNotFoundWhenRecordDisappearsBeforeWrite() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.updateLibraryResult = false;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.updateLibrary(LIBRARY_ID, new UpdateLibraryRequest("Travel Photos")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listLibrariesReportsUnavailableSourceHealth() throws Exception {
        Path missing = tempDir.resolve("missing");
        Path notDirectory = Files.writeString(tempDir.resolve("photo.jpg"), "image");
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.libraryRoots = List.of(
                new LibraryRepository.LibraryRootRecord(
                        ROOT_ID,
                        LIBRARY_ID,
                        missing.toString(),
                        missing.toAbsolutePath().normalize().toString(),
                        OffsetDateTime.now()
                ),
                new LibraryRepository.LibraryRootRecord(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        LIBRARY_ID,
                        notDirectory.toString(),
                        notDirectory.toAbsolutePath().normalize().toString(),
                        OffsetDateTime.now()
                )
        );
        LibraryService service = new LibraryService(repository);

        LibraryResponse response = service.listLibraries().getFirst();

        assertThat(response.availableSourceCount()).isZero();
        assertThat(response.unavailableSourceCount()).isEqualTo(2);
        assertThat(response.sources()).extracting(LibrarySourceResponse::unavailableReason)
                .containsExactly("missing", "not_directory");
    }

    @Test
    void listLibrariesReportsPermissionDeniedAndInvalidSourceHealth() throws Exception {
        Path readable = Files.createDirectory(tempDir.resolve("readable"));
        String invalidPath = "\0invalid";
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.libraryRoots = List.of(
                new LibraryRepository.LibraryRootRecord(
                        ROOT_ID,
                        LIBRARY_ID,
                        readable.toString(),
                        invalidPath,
                        OffsetDateTime.now()
                )
        );
        LibraryService service = new LibraryService(repository);

        LibraryResponse response = service.listLibraries().getFirst();

        assertThat(response.availableSourceCount()).isZero();
        assertThat(response.sources()).extracting(LibrarySourceResponse::unavailableReason)
                .containsExactly("unavailable");
    }

    @Test
    void addRootRejectsSymlinkSourceRoot() throws Exception {
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path link = tempDir.resolve("source-link");
        Files.createSymbolicLink(link, target);
        LibraryService service = new LibraryService(new StubLibraryRepository());

        assertThatThrownBy(() -> service.addRoot(LIBRARY_ID, new AddLibraryRootRequest(link.toString())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void addRootNormalizesTraversalBeforeDuplicateCheck() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.existingRootPath = source.toRealPath().toString();
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.addRoot(
                LIBRARY_ID,
                new AddLibraryRootRequest(source.resolve("..").resolve("source").toString())
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void addRootMapsDuplicateInsertRaceToConflict() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.addRootFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.addRoot(LIBRARY_ID, new AddLibraryRootRequest(source.toString())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void addRootRejectsUnreadableSourceRoot() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("locked"));
        assumeTrue(source.getFileSystem().supportedFileAttributeViews().contains("posix"));

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(source);
        Files.setPosixFilePermissions(source, Set.of());
        assumeTrue(!Files.isReadable(source) || !Files.isExecutable(source));

        try {
            LibraryService service = new LibraryService(new StubLibraryRepository());

        assertThatThrownBy(() -> service.addRoot(LIBRARY_ID, new AddLibraryRootRequest(source.toString())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        } finally {
            Files.setPosixFilePermissions(source, originalPermissions);
        }
    }

    @Test
    void deleteRootReturnsNotFoundWhenRootDoesNotBelongToLibrary() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.deleteRootResult = false;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.deleteRoot(LIBRARY_ID, ROOT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listAndDeleteGlobalExclusionPatternsUseRepositoryRecords() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.globalPatterns = List.of(new LibraryRepository.GlobalExclusionPatternRecord(
                ROOT_ID,
                "**/cache/**",
                OffsetDateTime.now()
        ));
        LibraryService service = new LibraryService(repository);

        List<LibraryExclusionPatternResponse> patterns = service.listGlobalExclusionPatterns();
        service.deleteGlobalExclusionPattern(ROOT_ID);

        assertThat(patterns).extracting(LibraryExclusionPatternResponse::pattern).containsExactly("**/cache/**");
        assertThat(repository.deletedGlobalPatternId).isEqualTo(ROOT_ID);
    }

    @Test
    void deleteGlobalExclusionPatternReturnsNotFoundWhenMissing() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.deleteGlobalPatternResult = false;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.deleteGlobalExclusionPattern(ROOT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void addGlobalExclusionPatternNormalizesBackslashesAndReturnsCreatedPattern() {
        StubLibraryRepository repository = new StubLibraryRepository();
        LibraryService service = new LibraryService(repository);

        LibraryExclusionPatternResponse response =
                service.addGlobalExclusionPattern(new AddExclusionPatternRequest(" **\\cache\\** "));

        assertThat(response.pattern()).isEqualTo("**/cache/**");
        assertThat(repository.addedGlobalExclusionPattern).isEqualTo("**/cache/**");
    }

    @Test
    void addGlobalExclusionPatternMapsExistingAndRacingDuplicatesToConflict() {
        StubLibraryRepository existingRepository = new StubLibraryRepository();
        existingRepository.globalExclusionPatternExists = true;
        LibraryService existingService = new LibraryService(existingRepository);

        assertThatThrownBy(() -> existingService.addGlobalExclusionPattern(
                new AddExclusionPatternRequest("**/cache/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        StubLibraryRepository raceRepository = new StubLibraryRepository();
        raceRepository.addGlobalExclusionFailure =
                new DataIntegrityViolationException("duplicate key value violates unique constraint");
        LibraryService raceService = new LibraryService(raceRepository);

        assertThatThrownBy(() -> raceService.addGlobalExclusionPattern(
                new AddExclusionPatternRequest("**/cache/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void addGlobalExclusionPatternRejectsAbsoluteAndTraversalPatterns() {
        LibraryService service = new LibraryService(new StubLibraryRepository());

        assertThatThrownBy(() -> service.addGlobalExclusionPattern(new AddExclusionPatternRequest("/private/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addGlobalExclusionPattern(new AddExclusionPatternRequest("../secrets/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addGlobalExclusionPattern(new AddExclusionPatternRequest("x".repeat(257))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addGlobalExclusionPattern(new AddExclusionPatternRequest("   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void addExclusionPatternMapsDuplicateKeyFailuresToConflict() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.addExclusionFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() ->
                service.addExclusionPattern(LIBRARY_ID, new AddExclusionPatternRequest("**/cache/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void addExclusionPatternMapsExistingDuplicateToConflict() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.exclusionPatternExists = true;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() ->
                service.addExclusionPattern(LIBRARY_ID, new AddExclusionPatternRequest("**/cache/**")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteExclusionPatternReturnsNotFoundWhenMissing() {
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.deleteExclusionPatternResult = false;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.deleteExclusionPattern(LIBRARY_ID, ROOT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void renameFolderMovesDirectoryAndRewritesPaths() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        Path events = Files.createDirectories(source.resolve("Events"));
        Files.writeString(events.resolve("photo.jpg"), "image");
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                source.toString(),
                source.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
        LibraryService service = new LibraryService(repository);

        RenameFolderResponse response = service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "Parties")
        );

        assertThat(response.name()).isEqualTo("Parties");
        assertThat(Files.exists(source.resolve("Events"))).isFalse();
        assertThat(Files.exists(source.resolve("Parties").resolve("photo.jpg"))).isTrue();
        assertThat(repository.rewrittenAssetPrefix).containsExactly(
                events.toAbsolutePath().normalize().toString(),
                source.resolve("Parties").toAbsolutePath().normalize().toString()
        );
        assertThat(repository.rewrittenRootPrefix).containsExactly(
                events.toAbsolutePath().normalize().toString(),
                source.resolve("Parties").toAbsolutePath().normalize().toString()
        );
    }

    @Test
    void renameFolderRollsBackFilesystemMoveWhenRepositoryRewriteFails() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        Path events = Files.createDirectories(source.resolve("Events"));
        Files.writeString(events.resolve("photo.jpg"), "image");
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                source.toString(),
                source.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
        repository.failRootRewrite = true;
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "Parties")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database rewrite failed");

        assertThat(Files.exists(events.resolve("photo.jpg"))).isTrue();
        assertThat(Files.exists(source.resolve("Parties"))).isFalse();
    }

    @Test
    void renameFolderRejectsFoldersOutsideLibraryRootsAndExistingTargets() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        Path events = Files.createDirectories(source.resolve("Events"));
        Files.createDirectories(source.resolve("Parties"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                source.toString(),
                source.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(outside.toAbsolutePath().normalize().toString(), "Renamed")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "Parties")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void renameFolderRejectsWhenTargetWouldOverlapAnotherLibraryRootOrSourceIsMissing() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        Path events = Files.createDirectories(source.resolve("Events"));
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                source.toString(),
                source.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
        repository.existingRootPath = source.resolve("Parties").toAbsolutePath().normalize().toString();
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "Parties")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        repository.existingRootPath = null;
        Files.delete(events);

        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "Parties")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void renameFolderRejectsLibrarySourceRoot() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        StubLibraryRepository repository = new StubLibraryRepository();
        String rootPath = source.toAbsolutePath().normalize().toString();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                rootPath,
                rootPath,
                OffsetDateTime.now()
        );
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.renameFolder(LIBRARY_ID, new RenameFolderRequest(rootPath, "Renamed")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void renameFolderRejectsUnsafeTargetNames() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("library-root"));
        Path events = Files.createDirectories(source.resolve("Events"));
        StubLibraryRepository repository = new StubLibraryRepository();
        repository.root = new LibraryRepository.LibraryRootRecord(
                ROOT_ID,
                LIBRARY_ID,
                source.toString(),
                source.toAbsolutePath().normalize().toString(),
                OffsetDateTime.now()
        );
        LibraryService service = new LibraryService(repository);

        assertThatThrownBy(() -> service.renameFolder(
                LIBRARY_ID,
                new RenameFolderRequest(events.toAbsolutePath().normalize().toString(), "../Escape")
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static final class StubLibraryRepository extends LibraryRepository {

        private String existingRootPath;
        private LibraryRootRecord root;
        private List<LibraryRootRecord> libraryRoots = List.of();
        private List<GlobalExclusionPatternRecord> globalPatterns = List.of();
        private String createdLibraryName;
        private UUID createdLibraryOwner;
        private UUID updatedLibraryId;
        private String updatedLibraryName;
        private String addedGlobalExclusionPattern;
        private UUID deletedGlobalPatternId;
        private List<String> rewrittenAssetPrefix;
        private List<String> rewrittenRootPrefix;
        private boolean failRootRewrite;
        private boolean libraryNameExistsExcluding;
        private boolean globalExclusionPatternExists;
        private boolean exclusionPatternExists;
        private boolean updateLibraryResult = true;
        private boolean deleteRootResult = true;
        private boolean deleteGlobalPatternResult = true;
        private boolean deleteExclusionPatternResult = true;
        private DataIntegrityViolationException createLibraryFailure;
        private DataIntegrityViolationException updateLibraryFailure;
        private DataIntegrityViolationException addGlobalExclusionFailure;
        private DataIntegrityViolationException addRootFailure;
        private DataIntegrityViolationException addExclusionFailure;

        private StubLibraryRepository() {
            super((SQLQueryFactory) null);
        }

        @Override
        public List<LibraryRecord> listLibraries() {
            return List.of(library(libraryRoots));
        }

        @Override
        public Optional<LibraryRecord> findLibrary(UUID libraryId) {
            if (root != null) {
                return Optional.of(library(List.of(root)));
            }
            return Optional.of(library(libraryRoots));
        }

        @Override
        public boolean libraryNameExists(String name) {
            return false;
        }

        @Override
        public boolean libraryNameExistsExcluding(String name, UUID excludeLibraryId) {
            return libraryNameExistsExcluding;
        }

        @Override
        public UUID createLibrary(String name, UUID creatorId) {
            if (createLibraryFailure != null) {
                throw createLibraryFailure;
            }
            createdLibraryName = name;
            createdLibraryOwner = creatorId;
            return LIBRARY_ID;
        }

        @Override
        public boolean updateLibraryName(UUID libraryId, String name) {
            if (updateLibraryFailure != null) {
                throw updateLibraryFailure;
            }
            updatedLibraryId = libraryId;
            updatedLibraryName = name;
            createdLibraryName = name;
            return updateLibraryResult;
        }

        @Override
        public List<GlobalExclusionPatternRecord> listGlobalExclusionPatterns() {
            if (!globalPatterns.isEmpty()) {
                return globalPatterns;
            }
            if (addedGlobalExclusionPattern != null) {
                return List.of(new GlobalExclusionPatternRecord(ROOT_ID, addedGlobalExclusionPattern, OffsetDateTime.now()));
            }
            return List.of();
        }

        @Override
        public UUID addGlobalExclusionPattern(String pattern) {
            if (addGlobalExclusionFailure != null) {
                throw addGlobalExclusionFailure;
            }
            addedGlobalExclusionPattern = pattern;
            return ROOT_ID;
        }

        @Override
        public boolean globalExclusionPatternExists(String pattern) {
            return globalExclusionPatternExists;
        }

        @Override
        public boolean deleteGlobalExclusionPattern(UUID patternId) {
            deletedGlobalPatternId = patternId;
            return deleteGlobalPatternResult;
        }

        @Override
        public boolean exclusionPatternExists(UUID libraryId, String pattern) {
            return exclusionPatternExists;
        }

        @Override
        public UUID addExclusionPattern(UUID libraryId, String pattern) {
            if (addExclusionFailure != null) {
                throw addExclusionFailure;
            }
            return ROOT_ID;
        }

        @Override
        public boolean rootPathExists(String normalizedPath) {
            return normalizedPath.equals(existingRootPath);
        }

        @Override
        public UUID addRoot(UUID libraryId, String path, String normalizedPath) {
            if (addRootFailure != null) {
                throw addRootFailure;
            }
            root = new LibraryRootRecord(ROOT_ID, libraryId, path, normalizedPath, OffsetDateTime.now());
            return ROOT_ID;
        }

        @Override
        public boolean deleteRoot(UUID libraryId, UUID rootId) {
            return deleteRootResult;
        }

        @Override
        public boolean deleteExclusionPattern(UUID libraryId, UUID patternId) {
            return deleteExclusionPatternResult;
        }

        @Override
        public void rewriteAssetFilePathPrefix(String oldPrefix, String newPrefix) {
            rewrittenAssetPrefix = List.of(oldPrefix, newPrefix);
        }

        @Override
        public void rewriteRootPathPrefix(String oldPrefix, String newPrefix) {
            if (failRootRewrite) {
                throw new IllegalStateException("database rewrite failed");
            }
            rewrittenRootPrefix = List.of(oldPrefix, newPrefix);
        }

        @Override
        boolean isDuplicateKey(DataIntegrityViolationException exception) {
            return exception.getMessage() != null && exception.getMessage().contains("duplicate key");
        }

        private LibraryRecord library(List<LibraryRootRecord> roots) {
            OffsetDateTime now = OffsetDateTime.now();
            String name = createdLibraryName == null ? "Family Photos" : createdLibraryName;
            return new LibraryRecord(LIBRARY_ID, name, "active", now, now, null, roots, List.of());
        }
    }
}
