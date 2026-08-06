package com.pixierge.api.albums;

import com.pixierge.api.assets.AssetBrowseResponse;
import com.pixierge.api.assets.AssetService;
import com.pixierge.api.identity.AuthenticatedUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlbumService {
  private static final int MAX_NAME_LENGTH = 80;
  private final AlbumRepository albumRepository;
  private final AssetService assetService;

  public AlbumService(AlbumRepository albumRepository, AssetService assetService) {
    this.albumRepository = albumRepository;
    this.assetService = assetService;
  }

  @Transactional(readOnly = true)
  public List<AlbumSummaryResponse> list(AuthenticatedUser user, String scope) {
    return ("shared".equalsIgnoreCase(scope)
            ? albumRepository.listShared(user.id())
            : albumRepository.list(user.id()))
        .stream().map(this::response).toList();
  }

  @Transactional
  public AlbumSummaryResponse create(CreateAlbumRequest request, AuthenticatedUser user) {
    String name = validateName(request.name());
    rejectReservedStarredName(name);
    try {
      return get(albumRepository.create(user.id(), name), user);
    } catch (DataIntegrityViolationException exception) {
      throw duplicate(exception);
    }
  }

  @Transactional(readOnly = true)
  public AlbumSummaryResponse get(UUID id, AuthenticatedUser user) {
    if (!albumRepository.canView(id, user.id())) throw notFound();
    return albumRepository
        .find(id, user.id())
        .or(
            () ->
                albumRepository.listShared(user.id()).stream()
                    .filter(album -> album.id().equals(id))
                    .findFirst())
        .map(this::response)
        .orElseThrow(this::notFound);
  }

  @Transactional
  public AlbumSummaryResponse getOrCreateStarred(AuthenticatedUser user) {
    return albumRepository
        .findByKind(user.id(), AlbumKind.STARRED)
        .map(this::response)
        .orElseGet(
            () -> {
              Optional<UUID> created = albumRepository.createStarredIfAbsent(user.id());
              if (created.isPresent()) {
                return get(created.get(), user);
              }
              return albumRepository
                  .findByKind(user.id(), AlbumKind.STARRED)
                  .map(this::response)
                  .orElseThrow(
                      () -> new IllegalStateException("Starred album missing after create"));
            });
  }

  @Transactional
  public AssetBrowseResponse browseStarredAssets(
      AuthenticatedUser user, Integer page, Integer pageSize) {
    AlbumSummaryResponse starred = getOrCreateStarred(user);
    return assetService.browseAlbumAssets(user, starred.id(), page, pageSize);
  }

  @Transactional
  public AlbumSummaryResponse update(UUID id, UpdateAlbumRequest request, AuthenticatedUser user) {
    if (!albumRepository.owns(id, user.id()))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the owner can update album details");
    AlbumRepository.AlbumRecord album =
        albumRepository.find(id, user.id()).orElseThrow(this::notFound);
    if (AlbumKind.STARRED.equals(album.kind()) && request.name() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starred cannot be renamed");
    }
    String name = request.name() == null ? null : validateName(request.name());
    if (name != null) {
      rejectReservedStarredName(name);
    }
    if (request.coverAssetId() != null) {
      // A cover must be readable in at least one of the caller's libraries.
      // Album membership itself is not required to choose a cover.
      if (!assetService.canReadAsset(user, request.coverAssetId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cover asset is not readable");
      }
    }
    try {
      if (!albumRepository.update(id, user.id(), name, request.coverAssetId())) {
        throw notFound();
      }
      return get(id, user);
    } catch (DataIntegrityViolationException exception) {
      throw duplicate(exception);
    }
  }

  @Transactional
  public void delete(UUID id, AuthenticatedUser user) {
    AlbumRepository.AlbumRecord album =
        albumRepository.find(id, user.id()).orElseThrow(this::notFound);
    if (AlbumKind.STARRED.equals(album.kind())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starred cannot be deleted");
    }
    if (!albumRepository.delete(id, user.id())) {
      throw notFound();
    }
  }

  @Transactional(readOnly = true)
  public AssetBrowseResponse browseAssets(
      UUID id, AuthenticatedUser user, Integer page, Integer pageSize) {
    if (!albumRepository.canView(id, user.id())) throw notFound();
    return assetService.browseAlbumAssets(user, id, page, pageSize);
  }

  @Transactional
  public void addItems(AddAlbumItemsRequest request, AuthenticatedUser user) {
    List<UUID> albumIds = distinct(request.albumIds());
    if (albumIds.isEmpty() || request.items() == null || request.items().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Albums and items are required");
    }
    for (AlbumAssetItemRequest item : request.items()) {
      assetService.requireReadableAssetInLibrary(user, item.assetId(), item.sourceLibraryId());
    }
    for (UUID albumId : albumIds) {
      if (!albumRepository.canEdit(albumId, user.id()))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Album is read-only");
      int position = albumRepository.nextPosition(albumId);
      for (AlbumAssetItemRequest item : request.items()) {
        if (albumRepository.add(
            albumId, item.assetId(), item.sourceLibraryId(), position, user.id())) {
          position++;
        }
      }
    }
  }

  @Transactional
  public void deleteItems(UUID albumId, DeleteAlbumItemsRequest request, AuthenticatedUser user) {
    if (!albumRepository.canEdit(albumId, user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Album is read-only");
    List<UUID> assetIds = distinct(request.assetIds());
    if (assetIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assets are required");
    }
    albumRepository.deleteItems(albumId, assetIds);
  }

  @Transactional(readOnly = true)
  public List<AlbumRepository.AlbumMemberRecord> members(UUID id, AuthenticatedUser user) {
    if (!albumRepository.owns(id, user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can manage members");
    return albumRepository.members(id);
  }

  @Transactional(readOnly = true)
  public List<AlbumRepository.AlbumMemberRecord> memberCandidates(UUID id, AuthenticatedUser user) {
    if (!albumRepository.owns(id, user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can manage members");
    return albumRepository.activeUsersExcept(user.id());
  }

  @Transactional
  public AlbumRepository.AlbumMemberRecord addMember(
      UUID id, UpsertAlbumMemberRequest request, AuthenticatedUser user) {
    if (!albumRepository.owns(id, user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can manage members");
    if (request == null
        || request.userId() == null
        || !List.of("viewer", "editor").contains(request.role()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A viewer or editor is required");
    if (request.userId().equals(user.id()) || !albumRepository.activeUser(request.userId()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Recipient must be another active user");
    albumRepository.upsertMember(id, request.userId(), request.role());
    return albumRepository.members(id).stream()
        .filter(member -> member.userId().equals(request.userId()))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void removeMember(UUID id, UUID memberId, AuthenticatedUser user) {
    if (!albumRepository.owns(id, user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can manage members");
    albumRepository.removeMember(id, memberId);
  }

  private AlbumSummaryResponse response(AlbumRepository.AlbumRecord album) {
    return new AlbumSummaryResponse(
        album.id(),
        album.name(),
        album.coverAssetId(),
        album.coverFileName(),
        album.kind(),
        album.itemCount(),
        album.sourceLibraryCount(),
        album.createdAt(),
        album.updatedAt());
  }

  private String validateName(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album name is required");
    }
    if (value.length() > MAX_NAME_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Album name is too long");
    }
    return value;
  }

  private void rejectReservedStarredName(String name) {
    if (AlbumKind.STARRED_NAME.equalsIgnoreCase(name)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starred is a reserved album name");
    }
  }

  private List<UUID> distinct(List<UUID> values) {
    return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
  }

  private ResponseStatusException duplicate(DataIntegrityViolationException exception) {
    if (isDuplicate(exception)) {
      return new ResponseStatusException(
          HttpStatus.CONFLICT, "Album name already exists", exception);
    }
    throw exception;
  }

  private boolean isDuplicate(DataIntegrityViolationException exception) {
    return exception.getMessage() != null && exception.getMessage().contains("duplicate key");
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found");
  }
}
