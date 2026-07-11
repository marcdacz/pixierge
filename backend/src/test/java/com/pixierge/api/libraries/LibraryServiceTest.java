package com.pixierge.api.libraries;

import com.querydsl.sql.SQLQueryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    private static final class StubLibraryRepository extends LibraryRepository {

        private String existingRootPath;
        private LibraryRootRecord root;
        private List<String> rewrittenAssetPrefix;
        private List<String> rewrittenRootPrefix;

        private StubLibraryRepository() {
            super((SQLQueryFactory) null);
        }

        @Override
        public Optional<LibraryRecord> findLibrary(UUID libraryId) {
            return Optional.of(library(root == null ? List.of() : List.of(root)));
        }

        @Override
        public boolean rootPathExists(String normalizedPath) {
            return normalizedPath.equals(existingRootPath);
        }

        @Override
        public UUID addRoot(UUID libraryId, String path, String normalizedPath) {
            root = new LibraryRootRecord(ROOT_ID, libraryId, path, normalizedPath, OffsetDateTime.now());
            return ROOT_ID;
        }

        @Override
        public void rewriteAssetFilePathPrefix(String oldPrefix, String newPrefix) {
            rewrittenAssetPrefix = List.of(oldPrefix, newPrefix);
        }

        @Override
        public void rewriteRootPathPrefix(String oldPrefix, String newPrefix) {
            rewrittenRootPrefix = List.of(oldPrefix, newPrefix);
        }

        private LibraryRecord library(List<LibraryRootRecord> roots) {
            OffsetDateTime now = OffsetDateTime.now();
            return new LibraryRecord(LIBRARY_ID, "Family Photos", "active", now, now, null, roots, List.of());
        }
    }
}
