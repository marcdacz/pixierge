package com.pixierge.api.assets;

import com.pixierge.api.identity.AuthenticatedUser;
import com.pixierge.api.tags.TagRepository;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.pixierge.api.assets.AssetConstants.DEFAULT_PAGE_SIZE;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_EXTRACTED;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_FAILED;
import static com.pixierge.api.assets.AssetConstants.EXTRACTION_STATUS_UNSUPPORTED;
import static com.pixierge.api.assets.AssetConstants.FILE_STATUS_ACTIVE;
import static com.pixierge.api.assets.AssetConstants.IDENTITY_STATUS_PENDING;
import static com.pixierge.api.assets.AssetConstants.IMAGE_MIME_PREFIX;
import static com.pixierge.api.assets.AssetConstants.MAX_PAGE_SIZE;
import static com.pixierge.api.assets.AssetConstants.METADATA_BACKFILL_BATCH_SIZE;
import static com.pixierge.api.libraries.LibraryConstants.PERMISSION_LIBRARY_ADMIN;

@Service
public class AssetService {

    private static final String METADATA_SOURCE_VERSION = "file-basic-v1";

    private final AssetRepository assetRepository;
    private final ThumbnailService thumbnailService;
    private final TagRepository tagRepository;

    public AssetService(AssetRepository assetRepository, ThumbnailService thumbnailService, TagRepository tagRepository) {
        this.assetRepository = assetRepository;
        this.thumbnailService = thumbnailService;
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public LibraryTreeResponse libraryTree(AuthenticatedUser user, UUID libraryId) {
        boolean admin = canAdminLibraries(user);
        Map<UUID, List<String>> rootsByLibrary = new LinkedHashMap<>();
        for (AssetRepository.LibraryRootRow rootRow : assetRepository.listLibraryRoots(user.id(), admin, libraryId)) {
            rootsByLibrary.computeIfAbsent(rootRow.libraryId(), ignored -> new ArrayList<>())
                    .add(rootRow.normalizedPath());
        }

        Map<String, MutableTreeNode> nodes = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> rootAssetIdsByLibrary = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> libraryAssetIds = new LinkedHashMap<>();

        for (AssetRepository.FolderRow row : assetRepository.listFolders(user.id(), admin, libraryId)) {
            libraryAssetIds.computeIfAbsent(row.libraryId(), ignored -> new LinkedHashSet<>()).add(row.assetId());

            List<String> roots = rootsByLibrary.getOrDefault(row.libraryId(), List.of());
            String root = matchingRoot(row.folderPath(), roots);
            List<String> parts;
            String pathPrefix;

            if (root != null) {
                parts = relativePathParts(row.folderPath(), root);
                pathPrefix = normalizePath(root);
            } else {
                parts = pathParts(row.folderPath());
                pathPrefix = "";
            }

            if (parts.isEmpty()) {
                if (root != null) {
                    rootAssetIdsByLibrary.computeIfAbsent(row.libraryId(), ignored -> new LinkedHashSet<>())
                            .add(row.assetId());
                }
                continue;
            }

            String currentPath = pathPrefix;
            MutableTreeNode parent = null;

            for (String part : parts) {
                currentPath = pathPrefix.isEmpty()
                        ? (currentPath.isEmpty() ? "/" + part : currentPath + "/" + part)
                        : currentPath + "/" + part;
                String nodePath = currentPath;
                String nodeId = row.libraryId() + ":" + nodePath;
                MutableTreeNode node = nodes.computeIfAbsent(nodeId, ignored ->
                        new MutableTreeNode(nodeId, row.libraryId(), row.libraryName(), nodePath, part));
                if (parent != null) {
                    parent.children.putIfAbsent(node.id, node);
                }
                parent = node;
            }

            if (parent != null) {
                parent.assetIds.add(row.assetId());
            }
        }

        List<LibraryTreeNodeResponse> roots = nodes.values().stream()
                .filter(node -> !hasParent(node, nodes))
                .sorted(Comparator.comparing(MutableTreeNode::name, String.CASE_INSENSITIVE_ORDER))
                .map(MutableTreeNode::toResponse)
                .toList();
        return new LibraryTreeResponse(
                roots,
                toAssetCountMap(rootAssetIdsByLibrary),
                toAssetCountMap(libraryAssetIds)
        );
    }

    @Transactional(readOnly = true)
    public AssetBrowseResponse browse(
            AuthenticatedUser user,
            UUID libraryId,
            String folder,
            Boolean includeDescendants,
            String query,
            String availability,
            String fileType,
            Boolean duplicatesOnly,
            List<UUID> tagIds,
            Integer page,
            Integer pageSize
    ) {
        int normalizedPage = Math.max(0, page == null ? 0 : page);
        int normalizedPageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize));
        AssetRepository.AssetSearchCriteria criteria = new AssetRepository.AssetSearchCriteria(
                libraryId,
                blankToNull(folder),
                includeDescendants == null || includeDescendants,
                blankToNull(query),
                blankToNull(availability),
                blankToNull(fileType),
                duplicatesOnly,
                tagIds,
                user.id(),
                normalizedPage,
                normalizedPageSize
        );
        AssetRepository.BrowseRows rows = assetRepository.browse(user.id(), canAdminLibraries(user), criteria);
        return toBrowseResponse(user, rows, normalizedPage, normalizedPageSize);
    }

    @Transactional(readOnly = true)
    public AssetBrowseResponse browseAlbumAssets(AuthenticatedUser user, UUID albumId, Integer page, Integer pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        return toBrowseResponse(user, assetRepository.browseAlbumAssets(user.id(), canAdminLibraries(user), albumId,
                normalizedPage, normalizedPageSize), normalizedPage, normalizedPageSize);
    }

    @Transactional(readOnly = true)
    public AssetBrowseResponse browseTagAssets(AuthenticatedUser user, UUID tagId, Integer page, Integer pageSize) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        return toBrowseResponse(user, assetRepository.browseTagAssets(user.id(), canAdminLibraries(user), tagId,
                normalizedPage, normalizedPageSize), normalizedPage, normalizedPageSize);
    }

    @Transactional(readOnly = true)
    public void requireReadableAssetInLibrary(AuthenticatedUser user, UUID assetId, UUID libraryId) {
        if (!assetRepository.canReadAsset(user.id(), canAdminLibraries(user), assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found");
        }
        if (!assetRepository.canReadAssetInLibrary(user.id(), canAdminLibraries(user), assetId, libraryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Asset is not readable in the source library");
        }
    }

    @Transactional(readOnly = true)
    public boolean canReadAsset(AuthenticatedUser user, UUID assetId) {
        return assetRepository.canReadAsset(user.id(), canAdminLibraries(user), assetId);
    }

    private AssetBrowseResponse toBrowseResponse(
            AuthenticatedUser user,
            AssetRepository.BrowseRows rows,
            int normalizedPage,
            int normalizedPageSize
    ) {
        Map<String, ThumbnailService.ThumbnailBrowseSummary> thumbnailSummaries = thumbnailService.browseSummaries(rows.assets().stream()
                .map(AssetRepository.AssetSummaryRow::contentHash)
                .toList());
        Set<UUID> favouritedIds = assetRepository.favouritedAssetIds(
                user.id(),
                rows.assets().stream().map(AssetRepository.AssetSummaryRow::assetId).toList()
        );
        Map<String, List<AssetSummaryResponse>> byFolder = new LinkedHashMap<>();

        for (AssetRepository.AssetSummaryRow row : rows.assets()) {
            ThumbnailService.ThumbnailBrowseSummary thumbnail = thumbnailSummary(row, thumbnailSummaries);
            AssetSummaryResponse response = new AssetSummaryResponse(
                    row.assetId(),
                    row.fileName(),
                    row.displayPath(),
                    row.folderPath(),
                    row.libraryId(),
                    row.libraryName(),
                    row.availability(),
                    row.identityStatus(),
                    row.duplicateCount(),
                    row.capturedAt(),
                    row.observedAt(),
                    row.mediaType(),
                    row.mimeType(),
                    row.width(),
                    row.height(),
                    row.previewable(),
                    thumbnail.status(),
                    thumbnail.cacheKey(),
                    thumbnail.placeholder(),
                    favouritedIds.contains(row.assetId())
            );
            byFolder.computeIfAbsent(row.folderPath(), ignored -> new ArrayList<>()).add(response);
        }

        List<AssetSectionResponse> sections = byFolder.entrySet().stream()
                .map(entry -> new AssetSectionResponse(entry.getKey(), folderName(entry.getKey()), entry.getValue()))
                .toList();
        return new AssetBrowseResponse(
                sections,
                rows.totalCount(),
                normalizedPage,
                normalizedPageSize,
                (normalizedPage + 1) * normalizedPageSize < rows.totalCount()
        );
    }

    private ThumbnailService.ThumbnailBrowseSummary thumbnailSummary(
            AssetRepository.AssetSummaryRow row,
            Map<String, ThumbnailService.ThumbnailBrowseSummary> thumbnailSummaries
    ) {
        if (!row.previewable()) {
            return ThumbnailService.ThumbnailBrowseSummary.missing();
        }
        if (IDENTITY_STATUS_PENDING.equals(row.identityStatus())) {
            return new ThumbnailService.ThumbnailBrowseSummary("pending", null, null);
        }
        return thumbnailSummaries.getOrDefault(row.contentHash(), ThumbnailService.ThumbnailBrowseSummary.missing());
    }

    @Transactional(readOnly = true)
    public AssetDetailResponse getAsset(AuthenticatedUser user, UUID assetId) {
        AssetRepository.AssetDetailRow detail = assetRepository.findAsset(user.id(), canAdminLibraries(user), assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
        return new AssetDetailResponse(
                detail.assetId(),
                detail.contentHash(),
                detail.identityStatus(),
                detail.mediaType(),
                detail.availability(),
                detail.duplicateCount(),
                detail.metadata(),
                detail.files(),
                tagRepository.listAssetTags(assetId, user.id())
        );
    }

    @Transactional(readOnly = true)
    public AssetFileResource file(AuthenticatedUser user, UUID assetId) {
        AssetDetailResponse asset = getAsset(user, assetId);
        AssetFileOccurrenceResponse activeFile = asset.files().stream()
                .filter(file -> FILE_STATUS_ACTIVE.equals(file.status()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active asset file not found"));
        Path path = Path.of(activeFile.path()).toAbsolutePath().normalize();

        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }

        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null || !contentType.startsWith(IMAGE_MIME_PREFIX)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Asset file cannot be previewed");
            }
            return new AssetFileResource(new PathResource(path), contentType, Files.size(path));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }
    }

    @Transactional
    public ThumbnailResponseResource thumbnail(AuthenticatedUser user, UUID assetId, String size) {
        AssetDetailResponse asset = getAsset(user, assetId);
        return switch (size == null ? "grid" : size) {
            case "tiny" -> thumbnailService.tinyThumbnail(asset);
            case "grid" -> thumbnailService.gridThumbnail(asset);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported thumbnail size");
        };
    }

    @Transactional(noRollbackFor = ResponseStatusException.class, propagation = Propagation.REQUIRED)
    public ThumbnailResponseResource preview(AuthenticatedUser user, UUID assetId) {
        AssetDetailResponse asset = getAsset(user, assetId);
        try {
            return thumbnailService.previewThumbnail(asset);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw exception;
            }
            return toPreviewFromOriginal(asset);
        }
    }

    @Transactional(readOnly = true)
    List<AssetDetailResponse> listConfirmedAssets() {
        return assetRepository.listConfirmedAssetIds().stream()
                .map(id -> assetRepository.findAsset(null, true, id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found")))
                .map(detail -> new AssetDetailResponse(
                        detail.assetId(),
                        detail.contentHash(),
                        detail.identityStatus(),
                        detail.mediaType(),
                        detail.availability(),
                        detail.duplicateCount(),
                        detail.metadata(),
                        detail.files(),
                        List.of()
                ))
                .toList();
    }

    @Transactional
    public ThumbnailAdminActionResponse rebuildMissingThumbnails() {
        return thumbnailService.rebuildMissing(this);
    }

    @Transactional
    public ThumbnailAdminActionResponse purgeStaleThumbnails() {
        return thumbnailService.purgeStale();
    }

    @Transactional
    public MetadataBackfillResponse backfillMetadata() {
        int processed = 0;
        int failed = 0;

        for (AssetRepository.MetadataCandidateRow candidate : assetRepository.listMetadataCandidates(METADATA_BACKFILL_BATCH_SIZE)) {
            MetadataResult result = extract(candidate);
            assetRepository.upsertMetadata(new AssetRepository.MetadataUpdate(
                    candidate.assetId(),
                    result.capturedAt(),
                    result.width(),
                    result.height(),
                    null,
                    extension(candidate.fileName()),
                    result.mimeType(),
                    null,
                    null,
                    METADATA_SOURCE_VERSION,
                    result.status(),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    result.errorMessage()
            ));
            assetRepository.upsertSearchDocument(
                    candidate.assetId(),
                    assetRepository.searchableTextForAsset(candidate.assetId()),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            processed++;
            if (EXTRACTION_STATUS_FAILED.equals(result.status())) {
                failed++;
            }
        }

        return new MetadataBackfillResponse(processed, failed);
    }

    private MetadataResult extract(AssetRepository.MetadataCandidateRow candidate) {
        Path path = Path.of(candidate.normalizedPath()).toAbsolutePath().normalize();
        String mimeType = null;
        try {
            mimeType = Files.probeContentType(path);
            try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
                if (input == null) {
                    return new MetadataResult(candidate.modifiedAt(), null, null, mimeType, EXTRACTION_STATUS_UNSUPPORTED, null);
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    return new MetadataResult(candidate.modifiedAt(), null, null, mimeType, EXTRACTION_STATUS_UNSUPPORTED, null);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0) {
                        return new MetadataResult(candidate.modifiedAt(), null, null, mimeType, EXTRACTION_STATUS_UNSUPPORTED, null);
                    }
                    return new MetadataResult(
                            candidate.modifiedAt(),
                            width,
                            height,
                            mimeType,
                            EXTRACTION_STATUS_EXTRACTED,
                            null
                    );
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | SecurityException exception) {
            return new MetadataResult(candidate.modifiedAt(), null, null, mimeType, EXTRACTION_STATUS_FAILED, exception.getMessage());
        }
    }

    private boolean canAdminLibraries(AuthenticatedUser user) {
        return user.permissions().contains(PERMISSION_LIBRARY_ADMIN);
    }

    private int normalizePage(Integer page) {
        return Math.max(0, page == null ? 0 : page);
    }

    private int normalizePageSize(Integer pageSize) {
        return Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize));
    }

    private boolean hasParent(MutableTreeNode node, Map<String, MutableTreeNode> nodes) {
        int lastSlash = node.path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return false;
        }
        String parentPath = node.path.substring(0, lastSlash);
        return nodes.containsKey(node.libraryId + ":" + parentPath);
    }

    private List<String> pathParts(String path) {
        String[] rawParts = path.split("/");
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (!rawPart.isBlank()) {
                parts.add(rawPart);
            }
        }
        return parts;
    }

    private String matchingRoot(String folderPath, List<String> roots) {
        String normalizedFolder = normalizePath(folderPath);
        String match = null;
        int matchLength = -1;

        for (String root : roots) {
            String normalizedRoot = normalizePath(root);
            if (normalizedFolder.equals(normalizedRoot) || normalizedFolder.startsWith(normalizedRoot + "/")) {
                if (normalizedRoot.length() > matchLength) {
                    match = root;
                    matchLength = normalizedRoot.length();
                }
            }
        }

        return match;
    }

    private List<String> relativePathParts(String folderPath, String root) {
        String normalizedFolder = normalizePath(folderPath);
        String normalizedRoot = normalizePath(root);
        if (normalizedFolder.equals(normalizedRoot)) {
            return List.of();
        }
        if (!normalizedFolder.startsWith(normalizedRoot + "/")) {
            return List.of();
        }

        String relative = normalizedFolder.substring(normalizedRoot.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank()) {
            return List.of();
        }
        return pathParts(relative);
    }

    private String normalizePath(String path) {
        try {
            return Path.of(path).normalize().toString();
        } catch (RuntimeException exception) {
            return path;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<UUID, Integer> toAssetCountMap(Map<UUID, Set<UUID>> assetIdsByLibrary) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : assetIdsByLibrary.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    private String folderName(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private ThumbnailResponseResource toPreviewFromOriginal(AssetDetailResponse asset) {
        AssetFileOccurrenceResponse activeFile = asset.files().stream()
                .filter(file -> FILE_STATUS_ACTIVE.equals(file.status()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active asset file not found"));
        Path path = Path.of(activeFile.path()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }
        String contentType;
        long size;
        try {
            contentType = Files.probeContentType(path);
            if (contentType == null || !contentType.startsWith(IMAGE_MIME_PREFIX)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Asset file cannot be previewed");
            }
            size = Files.size(path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is unavailable");
        }
        return new ThumbnailResponseResource(
                new PathResource(path),
                size,
                contentType,
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private record MetadataResult(
            OffsetDateTime capturedAt,
            Integer width,
            Integer height,
            String mimeType,
            String status,
            String errorMessage
    ) {
    }

    private static final class MutableTreeNode {
        private final String id;
        private final UUID libraryId;
        private final String libraryName;
        private final String path;
        private final String name;
        private final Map<String, MutableTreeNode> children = new LinkedHashMap<>();
        private final Set<UUID> assetIds = new LinkedHashSet<>();

        private MutableTreeNode(String id, UUID libraryId, String libraryName, String path, String name) {
            this.id = id;
            this.libraryId = libraryId;
            this.libraryName = libraryName;
            this.path = path;
            this.name = name;
        }

        private String name() {
            return name;
        }

        private Set<UUID> subtreeAssetIds() {
            Set<UUID> ids = new LinkedHashSet<>(assetIds);
            for (MutableTreeNode child : children.values()) {
                ids.addAll(child.subtreeAssetIds());
            }
            return ids;
        }

        private LibraryTreeNodeResponse toResponse() {
            List<LibraryTreeNodeResponse> childResponses = children.values().stream()
                    .sorted(Comparator.comparing(MutableTreeNode::name, String.CASE_INSENSITIVE_ORDER))
                    .map(MutableTreeNode::toResponse)
                    .toList();
            return new LibraryTreeNodeResponse(
                    id,
                    libraryId,
                    libraryName,
                    path,
                    name,
                    subtreeAssetIds().size(),
                    childResponses.size(),
                    childResponses
            );
        }
    }
}
