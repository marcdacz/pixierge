package com.pixierge.api.albums;

import com.pixierge.api.assets.AssetBrowseResponse;
import com.pixierge.api.assets.AssetService;
import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

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
    public List<AlbumSummaryResponse> list(AuthenticatedUser user) {
        return albumRepository.list(user.id()).stream().map(this::response).toList();
    }

    @Transactional
    public AlbumSummaryResponse create(CreateAlbumRequest request, AuthenticatedUser user) {
        String name = validateName(request.name());
        try {
            return get(albumRepository.create(user.id(), name), user);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional(readOnly = true)
    public AlbumSummaryResponse get(UUID id, AuthenticatedUser user) {
        return albumRepository.find(id, user.id()).map(this::response).orElseThrow(this::notFound);
    }

    @Transactional
    public AlbumSummaryResponse update(UUID id, UpdateAlbumRequest request, AuthenticatedUser user) {
        String name = request.name() == null ? null : validateName(request.name());
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
        if (!albumRepository.delete(id, user.id())) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public AssetBrowseResponse browseAssets(UUID id, AuthenticatedUser user, Integer page, Integer pageSize) {
        get(id, user);
        return assetService.browseAlbumAssets(user, id, page, pageSize);
    }

    @Transactional
    public void addItems(AddAlbumItemsRequest request, AuthenticatedUser user) {
        List<UUID> albumIds = distinct(request.albumIds());
        if (albumIds.isEmpty() || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Albums and items are required");
        }
        if (!albumRepository.ownsAll(user.id(), albumIds)) {
            throw notFound();
        }
        for (AlbumAssetItemRequest item : request.items()) {
            assetService.requireReadableAssetInLibrary(user, item.assetId(), item.sourceLibraryId());
        }
        for (UUID albumId : albumIds) {
            int position = albumRepository.nextPosition(albumId);
            for (AlbumAssetItemRequest item : request.items()) {
                if (albumRepository.add(albumId, item.assetId(), item.sourceLibraryId(), position, user.id())) {
                    position++;
                }
            }
        }
    }

    @Transactional
    public void deleteItems(UUID albumId, DeleteAlbumItemsRequest request, AuthenticatedUser user) {
        get(albumId, user);
        List<UUID> assetIds = distinct(request.assetIds());
        if (assetIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assets are required");
        }
        albumRepository.deleteItems(albumId, assetIds);
    }

    private AlbumSummaryResponse response(AlbumRepository.AlbumRecord album) {
        return new AlbumSummaryResponse(album.id(), album.name(), album.coverAssetId(), album.coverFileName(),
                album.itemCount(), album.sourceLibraryCount(), album.createdAt(), album.updatedAt());
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

    private List<UUID> distinct(List<UUID> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private ResponseStatusException duplicate(DataIntegrityViolationException exception) {
        if (isDuplicate(exception)) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Album name already exists", exception);
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
