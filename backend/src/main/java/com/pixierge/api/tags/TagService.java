package com.pixierge.api.tags;

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
import java.util.Locale;
import java.util.UUID;

@Service
public class TagService {

    private static final int MAX_NAME_LENGTH = 80;
    private final TagRepository tagRepository;
    private final AssetService assetService;

    public TagService(TagRepository tagRepository, AssetService assetService) {
        this.tagRepository = tagRepository;
        this.assetService = assetService;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list(AuthenticatedUser user) {
        return tagRepository.list(user.id()).stream().map(this::response).toList();
    }

    @Transactional
    public TagResponse create(CreateTagRequest request, AuthenticatedUser user) {
        String name = validate(request.name());
        try {
            return get(tagRepository.create(user.id(), name, name.toLowerCase(Locale.ROOT)), user);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public TagResponse rename(UUID tagId, UpdateTagRequest request, AuthenticatedUser user) {
        String name = validate(request.name());
        try {
            if (!tagRepository.rename(tagId, user.id(), name, name.toLowerCase(Locale.ROOT))) {
                throw notFound();
            }
            return get(tagId, user);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(exception);
        }
    }

    @Transactional
    public void delete(UUID tagId, AuthenticatedUser user) {
        if (!tagRepository.delete(tagId, user.id())) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public AssetBrowseResponse browseAssets(UUID tagId, AuthenticatedUser user, Integer page, Integer pageSize) {
        get(tagId, user);
        return assetService.browseTagAssets(user, tagId, page, pageSize);
    }

    @Transactional
    public void addAssignments(AssignAssetTagsRequest request, AuthenticatedUser user) {
        List<UUID> tagIds = distinct(request.tagIds());
        if (tagIds.isEmpty() || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags and items are required");
        }
        if (!tagRepository.ownsAll(user.id(), tagIds)) {
            throw notFound();
        }
        for (AssetItemRequest item : request.items()) {
            assetService.requireReadableAssetInLibrary(user, item.assetId(), item.sourceLibraryId());
        }
        for (UUID tagId : tagIds) {
            for (AssetItemRequest item : request.items()) {
                try {
                    tagRepository.add(tagId, item.assetId(), item.sourceLibraryId(), user.id());
                } catch (DataIntegrityViolationException exception) {
                    if (!isDuplicate(exception)) {
                        throw exception;
                    }
                }
            }
        }
    }

    @Transactional
    public void deleteAssignments(DeleteAssetTagsRequest request, AuthenticatedUser user) {
        List<UUID> tagIds = distinct(request.tagIds());
        List<UUID> assetIds = distinct(request.assetIds());
        if (tagIds.isEmpty() || assetIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags and assets are required");
        }
        if (!tagRepository.ownsAll(user.id(), tagIds)) {
            throw notFound();
        }
        tagRepository.deleteAssignments(user.id(), tagIds, assetIds);
    }

    private TagResponse get(UUID id, AuthenticatedUser user) {
        return tagRepository.find(id, user.id()).map(this::response).orElseThrow(this::notFound);
    }

    private TagResponse response(TagRepository.TagRecord tag) {
        return new TagResponse(tag.id(), tag.name(), tag.assetCount(), tag.createdAt(), tag.updatedAt());
    }

    private String validate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name is required");
        }
        if (value.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name is too long");
        }
        return value;
    }

    private List<UUID> distinct(List<UUID> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private ResponseStatusException duplicate(DataIntegrityViolationException exception) {
        if (isDuplicate(exception)) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Tag name already exists", exception);
        }
        throw exception;
    }

    private boolean isDuplicate(DataIntegrityViolationException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("duplicate key");
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found");
    }
}
