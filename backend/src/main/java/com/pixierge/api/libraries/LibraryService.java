package com.pixierge.api.libraries;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class LibraryService {

    private static final int MAX_LIBRARY_NAME_LENGTH = 80;
    private static final int MAX_PATH_LENGTH = 1_024;
    private static final int MAX_EXCLUSION_PATTERN_LENGTH = 256;

    private final LibraryRepository libraryRepository;

    public LibraryService(LibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    @Transactional(readOnly = true)
    public List<LibraryResponse> listLibraries() {
        return libraryRepository.listLibraries().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LibraryResponse createLibrary(CreateLibraryRequest request, UUID creatorId) {
        String name = validateLibraryName(request.name());
        if (libraryRepository.libraryNameExists(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Library name already exists");
        }

        UUID libraryId;
        try {
            libraryId = libraryRepository.createLibrary(name, creatorId);
        } catch (DataIntegrityViolationException exception) {
            if (libraryRepository.isDuplicateKey(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Library name already exists", exception);
            }
            throw exception;
        }

        return findLibrary(libraryId);
    }

    @Transactional(readOnly = true)
    public List<LibraryExclusionPatternResponse> listGlobalExclusionPatterns() {
        return libraryRepository.listGlobalExclusionPatterns().stream()
                .map(this::toGlobalExclusionPatternResponse)
                .toList();
    }

    @Transactional
    public LibraryExclusionPatternResponse addGlobalExclusionPattern(AddGlobalExclusionPatternRequest request) {
        String pattern = validateExclusionPattern(request.pattern());
        if (libraryRepository.globalExclusionPatternExists(pattern)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Global exclusion pattern already exists");
        }

        UUID patternId;
        try {
            patternId = libraryRepository.addGlobalExclusionPattern(pattern);
        } catch (DataIntegrityViolationException exception) {
            if (libraryRepository.isDuplicateKey(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Global exclusion pattern already exists", exception);
            }
            throw exception;
        }

        return libraryRepository.listGlobalExclusionPatterns().stream()
                .filter(candidate -> candidate.id().equals(patternId))
                .findFirst()
                .map(this::toGlobalExclusionPatternResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Global exclusion pattern not found"));
    }

    @Transactional
    public void deleteGlobalExclusionPattern(UUID patternId) {
        if (!libraryRepository.deleteGlobalExclusionPattern(patternId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Global exclusion pattern not found");
        }
    }

    @Transactional
    public LibraryResponse addRoot(UUID libraryId, AddLibraryRootRequest request) {
        SourcePath sourcePath = validateSourcePath(request.path());
        findLibraryRecord(libraryId);

        if (libraryRepository.rootPathExists(sourcePath.normalizedPath())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source path is already configured");
        }

        try {
            libraryRepository.addRoot(libraryId, sourcePath.path(), sourcePath.normalizedPath());
        } catch (DataIntegrityViolationException exception) {
            if (libraryRepository.isDuplicateKey(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Source path is already configured", exception);
            }
            throw exception;
        }

        return findLibrary(libraryId);
    }

    @Transactional
    public void deleteRoot(UUID libraryId, UUID rootId) {
        findLibraryRecord(libraryId);
        if (!libraryRepository.deleteRoot(libraryId, rootId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source path not found");
        }
    }

    @Transactional
    public LibraryResponse archiveLibrary(UUID libraryId) {
        findLibraryRecord(libraryId);
        libraryRepository.archiveLibrary(libraryId);
        return findLibrary(libraryId);
    }

    @Transactional
    public LibraryResponse restoreLibrary(UUID libraryId) {
        findLibraryRecord(libraryId);
        libraryRepository.restoreLibrary(libraryId);
        return findLibrary(libraryId);
    }

    private LibraryResponse findLibrary(UUID libraryId) {
        return toResponse(findLibraryRecord(libraryId));
    }

    private LibraryRepository.LibraryRecord findLibraryRecord(UUID libraryId) {
        return libraryRepository.findLibrary(libraryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library not found"));
    }

    private LibraryResponse toResponse(LibraryRepository.LibraryRecord library) {
        List<LibrarySourceResponse> sources = library.roots().stream()
                .map(this::toSourceResponse)
                .toList();
        List<LibraryExclusionPatternResponse> exclusionPatterns = library.exclusionPatterns().stream()
                .map(this::toExclusionPatternResponse)
                .toList();
        long available = sources.stream().filter(LibrarySourceResponse::available).count();

        return new LibraryResponse(
                library.id(),
                library.name(),
                library.status(),
                sources.size(),
                available,
                sources.size() - available,
                library.createdAt(),
                library.updatedAt(),
                library.archivedAt(),
                sources,
                exclusionPatterns
        );
    }

    private LibrarySourceResponse toSourceResponse(LibraryRepository.LibraryRootRecord root) {
        SourceHealth health = sourceHealth(root.normalizedPath());
        return new LibrarySourceResponse(
                root.id(),
                root.path(),
                health.available(),
                health.unavailableReason(),
                root.createdAt()
        );
    }

    @Transactional
    public LibraryResponse addExclusionPattern(UUID libraryId, AddLibraryExclusionPatternRequest request) {
        String pattern = validateExclusionPattern(request.pattern());
        findLibraryRecord(libraryId);
        if (libraryRepository.exclusionPatternExists(libraryId, pattern)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exclusion pattern already exists");
        }

        try {
            libraryRepository.addExclusionPattern(libraryId, pattern);
        } catch (DataIntegrityViolationException exception) {
            if (libraryRepository.isDuplicateKey(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Exclusion pattern already exists", exception);
            }
            throw exception;
        }

        return findLibrary(libraryId);
    }

    @Transactional
    public void deleteExclusionPattern(UUID libraryId, UUID patternId) {
        findLibraryRecord(libraryId);
        if (!libraryRepository.deleteExclusionPattern(libraryId, patternId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exclusion pattern not found");
        }
    }

    private LibraryExclusionPatternResponse toExclusionPatternResponse(
            LibraryRepository.LibraryExclusionPatternRecord pattern
    ) {
        return new LibraryExclusionPatternResponse(pattern.id(), pattern.pattern(), pattern.createdAt());
    }

    private LibraryExclusionPatternResponse toGlobalExclusionPatternResponse(
            LibraryRepository.GlobalExclusionPatternRecord pattern
    ) {
        return new LibraryExclusionPatternResponse(pattern.id(), pattern.pattern(), pattern.createdAt());
    }

    private SourceHealth sourceHealth(String normalizedPath) {
        try {
            Path path = Path.of(normalizedPath);
            if (!Files.exists(path)) {
                return new SourceHealth(false, "missing");
            }
            if (!Files.isDirectory(path)) {
                return new SourceHealth(false, "not_directory");
            }
            if (!Files.isReadable(path) || !Files.isExecutable(path)) {
                return new SourceHealth(false, "permission_denied");
            }
            return new SourceHealth(true, null);
        } catch (InvalidPathException | SecurityException exception) {
            return new SourceHealth(false, "unavailable");
        }
    }

    private String validateLibraryName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library name is required");
        }
        if (name.length() > MAX_LIBRARY_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library name is too long");
        }
        return name;
    }

    private String validateExclusionPattern(String rawPattern) {
        String pattern = rawPattern == null ? "" : rawPattern.trim().replace('\\', '/');
        if (pattern.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exclusion pattern is required");
        }
        if (pattern.length() > MAX_EXCLUSION_PATTERN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exclusion pattern is too long");
        }
        if (pattern.startsWith("/") || pattern.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exclusion pattern must be a relative glob");
        }
        return pattern;
    }

    private SourcePath validateSourcePath(String rawPath) {
        String input = rawPath == null ? "" : rawPath.trim();
        if (input.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path is required");
        }
        if (input.length() > MAX_PATH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path is too long");
        }

        Path path;
        try {
            path = Path.of(input).normalize();
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path is invalid", exception);
        }

        if (!path.isAbsolute()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path must be absolute");
        }
        path = path.toAbsolutePath().normalize();

        if (Files.isSymbolicLink(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path must not be a symbolic link");
        }
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path does not exist");
        }
        if (!Files.isDirectory(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path must be a directory");
        }
        if (!Files.isReadable(path) || !Files.isExecutable(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path is not readable");
        }

        String normalizedPath;
        try {
            normalizedPath = path.toRealPath().toString();
        } catch (IOException | SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path is unavailable", exception);
        }
        return new SourcePath(normalizedPath, normalizedPath);
    }

    private record SourcePath(String path, String normalizedPath) {
    }

    private record SourceHealth(boolean available, String unavailableReason) {
    }
}
