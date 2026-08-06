package com.pixierge.api.libraries;

import static com.pixierge.api.libraries.LibraryConstants.PERMISSION_LIBRARY_ADMIN;

import com.pixierge.api.identity.AuthenticatedUser;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LibraryService {

  private static final int MAX_LIBRARY_NAME_LENGTH = 80;
  private static final int MAX_FOLDER_NAME_LENGTH = 255;
  private static final int MAX_PATH_LENGTH = 1_024;
  private static final int MAX_EXCLUSION_PATTERN_LENGTH = 256;

  private final LibraryRepository libraryRepository;

  public LibraryService(LibraryRepository libraryRepository) {
    this.libraryRepository = libraryRepository;
  }

  @Transactional(readOnly = true)
  public List<LibraryResponse> listLibraries(AuthenticatedUser user) {
    return libraryRepository.listLibraries().stream()
        .filter(
            library ->
                canAdminLibraries(user)
                    || ("active".equals(library.status())
                        && libraryRepository.isMember(library.id(), user.id())))
        .map(this::toResponse)
        .toList();
  }

  // Kept for focused legacy service tests; HTTP callers always supply their
  // authenticated user.
  @Transactional(readOnly = true)
  List<LibraryResponse> listLibraries() {
    return libraryRepository.listLibraries().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<LibraryMemberResponse> listMembers(UUID libraryId, AuthenticatedUser user) {
    requireMembershipManager(libraryId, user);
    return libraryRepository.listMembers(libraryId).stream().map(this::toMemberResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<LibraryMemberResponse> listMemberCandidates(UUID libraryId, AuthenticatedUser user) {
    requireMembershipManager(libraryId, user);
    return libraryRepository.listActiveUsers().stream().map(this::toMemberResponse).toList();
  }

  @Transactional
  public LibraryMemberResponse addMember(
      UUID libraryId, AddLibraryMemberRequest request, AuthenticatedUser user) {
    requireMembershipManager(libraryId, user);
    if (request.userId() == null || !libraryRepository.activeUserExists(request.userId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member must be an active user");
    }
    String role = validateMemberRole(request.role());
    if (!libraryRepository.addMember(libraryId, request.userId(), role)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a library member");
    }
    return libraryRepository.listMembers(libraryId).stream()
        .filter(member -> member.userId().equals(request.userId()))
        .findFirst()
        .map(this::toMemberResponse)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library member not found"));
  }

  @Transactional
  public LibraryMemberResponse changeMemberRole(
      UUID libraryId, UUID userId, ChangeLibraryMemberRoleRequest request, AuthenticatedUser user) {
    requireMembershipManager(libraryId, user);
    LibraryRepository.LibraryMemberRecord member =
        libraryRepository.listMembers(libraryId).stream()
            .filter(candidate -> candidate.userId().equals(userId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Library member not found"));
    String role = validateMemberRole(request.role());
    requireOwnerRetained(libraryId, member.role(), role);
    libraryRepository.updateMemberRole(libraryId, userId, role);
    return libraryRepository.listMembers(libraryId).stream()
        .filter(candidate -> candidate.userId().equals(userId))
        .findFirst()
        .map(this::toMemberResponse)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library member not found"));
  }

  @Transactional
  public void removeMember(UUID libraryId, UUID userId, AuthenticatedUser user) {
    requireMembershipManager(libraryId, user);
    LibraryRepository.LibraryMemberRecord member =
        libraryRepository.listMembers(libraryId).stream()
            .filter(candidate -> candidate.userId().equals(userId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Library member not found"));
    requireOwnerRetained(libraryId, member.role(), null);
    libraryRepository.removeMember(libraryId, userId);
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
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Library name already exists", exception);
      }
      throw exception;
    }

    return findLibrary(libraryId);
  }

  @Transactional
  public LibraryResponse updateLibrary(UUID libraryId, UpdateLibraryRequest request) {
    findLibraryRecord(libraryId);
    String name = validateLibraryName(request.name());
    if (libraryRepository.libraryNameExistsExcluding(name, libraryId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Library name already exists");
    }

    try {
      if (!libraryRepository.updateLibraryName(libraryId, name)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Library not found");
      }
    } catch (DataIntegrityViolationException exception) {
      if (libraryRepository.isDuplicateKey(exception)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Library name already exists", exception);
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
  public LibraryExclusionPatternResponse addGlobalExclusionPattern(
      AddExclusionPatternRequest request) {
    String pattern = validateExclusionPattern(request.pattern());
    if (libraryRepository.globalExclusionPatternExists(pattern)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Global exclusion pattern already exists");
    }

    UUID patternId;
    try {
      patternId = libraryRepository.addGlobalExclusionPattern(pattern);
    } catch (DataIntegrityViolationException exception) {
      if (libraryRepository.isDuplicateKey(exception)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Global exclusion pattern already exists", exception);
      }
      throw exception;
    }

    return libraryRepository.listGlobalExclusionPatterns().stream()
        .filter(candidate -> candidate.id().equals(patternId))
        .findFirst()
        .map(this::toGlobalExclusionPatternResponse)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Global exclusion pattern not found"));
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
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Source path is already configured", exception);
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
  public RenameFolderResponse renameFolder(UUID libraryId, RenameFolderRequest request) {
    LibraryRepository.LibraryRecord library = findLibraryRecord(libraryId);
    String oldPath = normalizeAbsolutePath(request.path());
    String newName = validateFolderName(request.name());

    LibraryRepository.LibraryRootRecord matchingRoot = matchingRoot(library.roots(), oldPath);
    if (matchingRoot == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Folder is not under a library source");
    }
    if (oldPath.equals(normalizeAbsolutePath(matchingRoot.normalizedPath()))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Library source roots cannot be renamed here");
    }

    Path source = Path.of(oldPath);
    Path target =
        source.getParent() == null ? Path.of(newName) : source.getParent().resolve(newName);
    String newPath = normalizeAbsolutePath(target.toString());
    if (newPath.equals(oldPath)) {
      return new RenameFolderResponse(newPath, newName);
    }
    if (Files.exists(target)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A folder with that name already exists");
    }
    if (libraryRepository.rootPathExists(newPath)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A library source already uses that path");
    }
    if (!Files.exists(source) || !Files.isDirectory(source)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder does not exist on disk");
    }

    try {
      Files.move(source, target);
    } catch (IOException exception) {
      throw folderRenameFailure(exception);
    }

    try {
      libraryRepository.rewriteAssetFilePathPrefix(oldPath, newPath);
      libraryRepository.rewriteRootPathPrefix(oldPath, newPath);
    } catch (RuntimeException exception) {
      try {
        Files.move(target, source);
      } catch (IOException ignored) {
        // Best-effort rollback of the filesystem move.
      }
      throw exception;
    }

    return new RenameFolderResponse(newPath, newName);
  }

  private ResponseStatusException folderRenameFailure(IOException exception) {
    if (exception instanceof AccessDeniedException || isReadOnlyFileSystem(exception)) {
      return new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Folder could not be renamed because the source path is not writable. Mount the library source as read-write and try again.",
          exception);
    }
    return new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR, "Folder could not be renamed", exception);
  }

  private boolean isReadOnlyFileSystem(IOException exception) {
    String message = exception.getMessage();
    return message != null && message.toLowerCase().contains("read-only");
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

  private void requireMembershipManager(UUID libraryId, AuthenticatedUser user) {
    findLibraryRecord(libraryId);
    if (!canAdminLibraries(user) && !libraryRepository.hasManagementAccess(libraryId, user.id())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Library membership requires an owner or administrator");
    }
  }

  private void requireOwnerRetained(UUID libraryId, String currentRole, String replacementRole) {
    if ("owner".equals(currentRole)
        && !"owner".equals(replacementRole)
        && libraryRepository.ownerCount(libraryId) <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A library must retain at least one owner");
    }
  }

  private String validateMemberRole(String rawRole) {
    String role = rawRole == null ? "" : rawRole.trim().toLowerCase(java.util.Locale.ROOT);
    if (!List.of("owner", "admin", "member").contains(role)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Library member role must be owner, admin, or member");
    }
    return role;
  }

  private LibraryMemberResponse toMemberResponse(LibraryRepository.LibraryMemberRecord member) {
    return new LibraryMemberResponse(
        member.userId(), member.username(), member.role(), member.createdAt());
  }

  private boolean canAdminLibraries(AuthenticatedUser user) {
    return user.permissions().contains(PERMISSION_LIBRARY_ADMIN);
  }

  private LibraryRepository.LibraryRecord findLibraryRecord(UUID libraryId) {
    return libraryRepository
        .findLibrary(libraryId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library not found"));
  }

  private LibraryResponse toResponse(LibraryRepository.LibraryRecord library) {
    List<LibrarySourceResponse> sources =
        library.roots().stream().map(this::toSourceResponse).toList();
    List<LibraryExclusionPatternResponse> exclusionPatterns =
        library.exclusionPatterns().stream().map(this::toExclusionPatternResponse).toList();
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
        exclusionPatterns);
  }

  private LibrarySourceResponse toSourceResponse(LibraryRepository.LibraryRootRecord root) {
    SourceHealth health = sourceHealth(root.normalizedPath());
    return new LibrarySourceResponse(
        root.id(), root.path(), health.available(), health.unavailableReason(), root.createdAt());
  }

  @Transactional
  public LibraryResponse addExclusionPattern(UUID libraryId, AddExclusionPatternRequest request) {
    String pattern = validateExclusionPattern(request.pattern());
    findLibraryRecord(libraryId);
    if (libraryRepository.exclusionPatternExists(libraryId, pattern)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Exclusion pattern already exists");
    }

    try {
      libraryRepository.addExclusionPattern(libraryId, pattern);
    } catch (DataIntegrityViolationException exception) {
      if (libraryRepository.isDuplicateKey(exception)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Exclusion pattern already exists", exception);
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
      LibraryRepository.LibraryExclusionPatternRecord pattern) {
    return new LibraryExclusionPatternResponse(
        pattern.id(), pattern.pattern(), pattern.createdAt());
  }

  private LibraryExclusionPatternResponse toGlobalExclusionPatternResponse(
      LibraryRepository.GlobalExclusionPatternRecord pattern) {
    return new LibraryExclusionPatternResponse(
        pattern.id(), pattern.pattern(), pattern.createdAt());
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

  private String validateFolderName(String rawName) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder name is required");
    }
    if (name.length() > MAX_FOLDER_NAME_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder name is too long");
    }
    if (name.contains("/")
        || name.contains("\\")
        || name.contains("..")
        || name.equals(".")
        || name.contains("\0")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder name is invalid");
    }
    return name;
  }

  private String normalizeAbsolutePath(String rawPath) {
    String input = rawPath == null ? "" : rawPath.trim();
    if (input.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is required");
    }
    if (input.length() > MAX_PATH_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path is too long");
    }
    try {
      Path path = Path.of(input).toAbsolutePath().normalize();
      if (!path.isAbsolute()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder path must be absolute");
      }
      return path.toString();
    } catch (InvalidPathException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Folder path is invalid", exception);
    }
  }

  private LibraryRepository.LibraryRootRecord matchingRoot(
      List<LibraryRepository.LibraryRootRecord> roots, String folderPath) {
    LibraryRepository.LibraryRootRecord match = null;
    int matchLength = -1;
    for (LibraryRepository.LibraryRootRecord root : roots) {
      String normalizedRoot = normalizeAbsolutePath(root.normalizedPath());
      if (folderPath.equals(normalizedRoot) || folderPath.startsWith(normalizedRoot + "/")) {
        if (normalizedRoot.length() > matchLength) {
          match = root;
          matchLength = normalizedRoot.length();
        }
      }
    }
    return match;
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
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Exclusion pattern must be a relative glob");
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
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Source path is invalid", exception);
    }

    if (!path.isAbsolute()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source path must be absolute");
    }
    path = path.toAbsolutePath().normalize();

    if (Files.isSymbolicLink(path)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Source path must not be a symbolic link");
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
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Source path is unavailable", exception);
    }
    return new SourcePath(normalizedPath, normalizedPath);
  }

  private record SourcePath(String path, String normalizedPath) {}

  private record SourceHealth(boolean available, String unavailableReason) {}
}
