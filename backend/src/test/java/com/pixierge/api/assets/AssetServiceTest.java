package com.pixierge.api.assets;

import com.pixierge.api.identity.AuthenticatedUser;
import com.pixierge.api.search.SearchParser;
import com.pixierge.api.tags.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID OTHER_LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID READY_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000304");
    private static final UUID PENDING_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000305");
    private static final UUID VIDEO_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000306");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-13T00:00:00Z");

    @TempDir
    private Path tempDir;

    private FakeAssetRepository assetRepository;
    private FakeThumbnailService thumbnailService;
    private FakeTagRepository tagRepository;
    private AssetService service;

    @BeforeEach
    void setUp() {
        assetRepository = new FakeAssetRepository();
        thumbnailService = new FakeThumbnailService();
        tagRepository = new FakeTagRepository();
        service = new AssetService(assetRepository, thumbnailService, tagRepository, new SearchParser());
    }

    @Test
    void browseNormalizesPagingAndMapsThumbnailsStarredAndFolderSections() {
        assetRepository.browseRows = new AssetRepository.BrowseRows(List.of(
                summary(READY_ASSET_ID, "confirmed-hash", "confirmed", "image/jpeg", true,
                        "/photos/Trips/japan.jpg", "japan.jpg", LIBRARY_ID),
                summary(PENDING_ASSET_ID, "provisional:scan", "pending", "image/jpeg", true,
                        "/photos/Trips/pending.jpg", "pending.jpg", LIBRARY_ID),
                summary(VIDEO_ASSET_ID, "video-hash", "confirmed", "video/mp4", false,
                        "/photos/Videos/clip.mp4", "clip.mp4", LIBRARY_ID)
        ), 150);
        assetRepository.starredIds = Set.of(READY_ASSET_ID);
        thumbnailService.summaries.put("confirmed-hash",
                new ThumbnailService.ThumbnailBrowseSummary("ready", "cache-key", "placeholder"));

        AssetBrowseResponse response = service.browse(
                user(),
                LIBRARY_ID,
                "   /photos   ",
                null,
                " trip ",
                "available",
                "image",
                true,
                List.of(UUID.randomUUID()),
                -5,
                999
        );

        assertThat(assetRepository.lastCriteria.page()).isZero();
        assertThat(assetRepository.lastCriteria.pageSize()).isEqualTo(120);
        assertThat(assetRepository.lastCriteria.folder()).isEqualTo("/photos");
        assertThat(assetRepository.lastCriteria.query().freeText()).isEqualTo("trip");
        assertThat(response.totalCount()).isEqualTo(150);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.sections()).hasSize(2);
        assertThat(response.sections().get(0).folderName()).isEqualTo("Trips");
        assertThat(response.sections().get(0).assets())
                .extracting(AssetSummaryResponse::id, AssetSummaryResponse::thumbnailStatus, AssetSummaryResponse::starred)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(READY_ASSET_ID, "ready", true),
                        org.assertj.core.groups.Tuple.tuple(PENDING_ASSET_ID, "pending", false)
                );
        assertThat(response.sections().get(1).assets().get(0).thumbnailStatus()).isEqualTo("missing");
    }

    @Test
    void libraryTreeBuildsRelativeFoldersAndSeparatesRootLevelAssetCounts() {
        UUID rootAssetId = UUID.fromString("00000000-0000-0000-0000-000000000308");
        UUID japanAssetId = UUID.fromString("00000000-0000-0000-0000-000000000309");
        UUID franceAssetId = UUID.fromString("00000000-0000-0000-0000-000000000310");
        assetRepository.libraryRoots = List.of(new AssetRepository.LibraryRootRow(LIBRARY_ID, "/photos"));
        assetRepository.folderRows = List.of(
                new AssetRepository.FolderRow(LIBRARY_ID, "Family Photos", "/photos", rootAssetId),
                new AssetRepository.FolderRow(LIBRARY_ID, "Family Photos", "/photos/Trips/Japan", japanAssetId),
                new AssetRepository.FolderRow(LIBRARY_ID, "Family Photos", "/photos/Trips/France", franceAssetId)
        );

        LibraryTreeResponse response = service.libraryTree(user(), LIBRARY_ID);

        assertThat(response.libraryRootAssetCounts()).containsEntry(LIBRARY_ID, 1);
        assertThat(response.libraryAssetCounts()).containsEntry(LIBRARY_ID, 3);
        assertThat(response.roots()).singleElement().satisfies(root -> {
            assertThat(root.name()).isEqualTo("Trips");
            assertThat(root.assetCount()).isEqualTo(2);
            assertThat(root.childCount()).isEqualTo(2);
            assertThat(root.children()).extracting(LibraryTreeNodeResponse::name)
                    .containsExactly("France", "Japan");
        });
    }

    @Test
    void browseTagAssetsNormalizesPagingAndUsesBrowseResponseMapping() {
        UUID tagId = UUID.fromString("00000000-0000-0000-0000-000000000311");
        assetRepository.tagBrowseRows = new AssetRepository.BrowseRows(List.of(
                summary(READY_ASSET_ID, "confirmed-hash", "confirmed", "image/jpeg", true,
                        "/photos/Tags/family.jpg", "family.jpg", LIBRARY_ID)
        ), 150);

        AssetBrowseResponse response = service.browseTagAssets(user(), tagId, -1, 999);

        assertThat(assetRepository.lastBrowsedTag).isEqualTo(tagId);
        assertThat(assetRepository.lastTagPage).isZero();
        assertThat(assetRepository.lastTagPageSize).isEqualTo(120);
        assertThat(response.totalCount()).isEqualTo(150);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void canReadAssetDelegatesWithCurrentUserAndAdminStatus() {
        assetRepository.canReadAsset = true;

        assertThat(service.canReadAsset(user(), READY_ASSET_ID)).isTrue();

        assertThat(assetRepository.lastCanReadUserId).isEqualTo(USER_ID);
        assertThat(assetRepository.lastCanReadAssetId).isEqualTo(READY_ASSET_ID);
        assertThat(assetRepository.lastCanReadAdmin).isFalse();
    }

    @Test
    void requireReadableAssetReportsMissingBeforeForbiddenLibraryContext() {
        assetRepository.canReadAsset = false;

        assertThatThrownBy(() -> service.requireReadableAssetInLibrary(user(), READY_ASSET_ID, LIBRARY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        assetRepository.canReadAsset = true;
        assetRepository.canReadAssetInLibrary = false;

        assertThatThrownBy(() -> service.requireReadableAssetInLibrary(user(), READY_ASSET_ID, OTHER_LIBRARY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getAssetIncludesOnlyTagsVisibleToTheCurrentUser() {
        AssetRepository.AssetDetailRow row = detailRow(READY_ASSET_ID, "confirmed-hash", "image/jpeg",
                List.of(file(READY_ASSET_ID, LIBRARY_ID, "/photos/ready.jpg", "ready.jpg", "active")));
        assetRepository.details.put(READY_ASSET_ID, row);
        UUID tagId = UUID.fromString("00000000-0000-0000-0000-000000000307");
        tagRepository.tags = List.of(new AssetTagResponse(tagId, "Family"));

        AssetDetailResponse response = service.getAsset(user(), READY_ASSET_ID);

        assertThat(response.id()).isEqualTo(READY_ASSET_ID);
        assertThat(response.tags()).extracting(AssetTagResponse::name).containsExactly("Family");
        assertThat(tagRepository.lastUserId).isEqualTo(USER_ID);
    }

    @Test
    void fileRejectsReadableAssetWhenActiveOccurrenceIsNotAnImage() throws Exception {
        Path video = Files.writeString(tempDir.resolve("clip.txt"), "not an image");
        assetRepository.details.put(VIDEO_ASSET_ID, detailRow(VIDEO_ASSET_ID, "video-hash", "video/mp4",
                List.of(file(VIDEO_ASSET_ID, LIBRARY_ID, video.toString(), "clip.txt", "active"))));

        assertThatThrownBy(() -> service.file(user(), VIDEO_ASSET_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void fileReturnsReadableImageResourceAndRejectsAssetsWithoutActiveFiles() throws Exception {
        Path image = tempDir.resolve("photo.jpg");
        Files.write(image, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        assetRepository.details.put(READY_ASSET_ID, detailRow(READY_ASSET_ID, "confirmed-hash", "image/jpeg",
                List.of(file(READY_ASSET_ID, LIBRARY_ID, image.toString(), "photo.jpg", "active"))));
        assetRepository.details.put(PENDING_ASSET_ID, detailRow(PENDING_ASSET_ID, "pending-hash", "image/jpeg",
                List.of(file(PENDING_ASSET_ID, LIBRARY_ID, tempDir.resolve("missing.jpg").toString(), "missing.jpg", "missing"))));

        AssetFileResource resource = service.file(user(), READY_ASSET_ID);

        assertThat(resource.contentType()).isEqualTo("image/jpeg");
        assertThat(resource.contentLength()).isEqualTo(Files.size(image));
        assertThatThrownBy(() -> service.file(user(), PENDING_ASSET_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void thumbnailRoutesSupportedSizesAndRejectsUnknownSizes() {
        assetRepository.details.put(READY_ASSET_ID, detailRow(READY_ASSET_ID, "confirmed-hash", "image/jpeg",
                List.of(file(READY_ASSET_ID, LIBRARY_ID, "/photos/ready.jpg", "ready.jpg", "active"))));

        ThumbnailResponseResource tiny = service.thumbnail(user(), READY_ASSET_ID, "tiny");
        ThumbnailResponseResource grid = service.thumbnail(user(), READY_ASSET_ID, null);

        assertThat(tiny.etag()).isEqualTo("tiny");
        assertThat(grid.etag()).isEqualTo("grid");
        assertThatThrownBy(() -> service.thumbnail(user(), READY_ASSET_ID, "poster"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void previewFallsBackToOriginalWhenGeneratedPreviewIsMissing() throws Exception {
        Path source = tempDir.resolve("photo.jpg");
        Files.write(source, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        assetRepository.details.put(READY_ASSET_ID, detailRow(READY_ASSET_ID, "confirmed-hash", "image/jpeg",
                List.of(file(READY_ASSET_ID, LIBRARY_ID, source.toString(), "photo.jpg", "active"))));
        thumbnailService.previewException = new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing preview");

        ThumbnailResponseResource response = service.preview(user(), READY_ASSET_ID);

        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.contentLength()).isEqualTo(Files.size(source));
    }

    @Test
    void previewRethrowsNonMissingThumbnailFailures() {
        assetRepository.details.put(READY_ASSET_ID, detailRow(READY_ASSET_ID, "confirmed-hash", "image/jpeg",
                List.of(file(READY_ASSET_ID, LIBRARY_ID, "/photos/ready.jpg", "ready.jpg", "active"))));
        thumbnailService.previewException = new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "No preview");

        assertThatThrownBy(() -> service.preview(user(), READY_ASSET_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void backfillMetadataRecordsUnsupportedAndFailedExtractionsAndRefreshesSearchDocuments() throws Exception {
        Path text = Files.writeString(tempDir.resolve("notes.txt"), "not an image");
        Path corruptImage = Files.write(tempDir.resolve("corrupt.gif"), "GIF89a".getBytes());
        assetRepository.metadataCandidates = List.of(
                new AssetRepository.MetadataCandidateRow(
                        READY_ASSET_ID,
                        UUID.randomUUID(),
                        text.toString(),
                        text.toString(),
                        "notes.txt",
                        NOW,
                        "image"
                ),
                new AssetRepository.MetadataCandidateRow(
                        VIDEO_ASSET_ID,
                        UUID.randomUUID(),
                        corruptImage.toString(),
                        corruptImage.toString(),
                        "corrupt.gif",
                        NOW,
                        "image"
                )
        );
        assetRepository.searchTextByAsset.put(READY_ASSET_ID, "notes txt");
        assetRepository.searchTextByAsset.put(VIDEO_ASSET_ID, "missing");

        MetadataBackfillResponse response = service.backfillMetadata();

        assertThat(response.processedCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(assetRepository.metadataUpdates)
                .extracting(AssetRepository.MetadataUpdate::assetId, AssetRepository.MetadataUpdate::fileExtension,
                        AssetRepository.MetadataUpdate::extractionStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(READY_ASSET_ID, "txt", "unsupported"),
                        org.assertj.core.groups.Tuple.tuple(VIDEO_ASSET_ID, "gif", "failed")
                );
        assertThat(assetRepository.searchUpserts).containsExactly(READY_ASSET_ID, VIDEO_ASSET_ID);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(USER_ID, "owner", Set.of(), Set.of(), "csrf");
    }

    private AssetRepository.AssetSummaryRow summary(
            UUID assetId,
            String contentHash,
            String identityStatus,
            String mimeType,
            boolean previewable,
            String path,
            String fileName,
            UUID libraryId
    ) {
        String folder = path.substring(0, path.lastIndexOf('/'));
        return new AssetRepository.AssetSummaryRow(
                assetId,
                fileName,
                path,
                folder,
                libraryId,
                "Family Photos",
                "available",
                identityStatus,
                1,
                NOW,
                NOW,
                mimeType,
                mimeType,
                100,
                100,
                contentHash,
                previewable
        );
    }

    private AssetRepository.AssetFileRow file(
            UUID assetId,
            UUID libraryId,
            String path,
            String fileName,
            String status
    ) {
        return new AssetRepository.AssetFileRow(
                assetId,
                "confirmed-hash",
                fileName.endsWith(".mp4") || fileName.endsWith(".txt") ? "video" : "image",
                "active".equals(status) ? 1 : 0,
                NOW,
                UUID.randomUUID(),
                libraryId,
                "Family Photos",
                path,
                path,
                fileName,
                4L,
                NOW,
                status,
                NOW,
                100,
                100,
                fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : null,
                fileName.endsWith(".jpg") ? "image/jpeg" : "video/mp4",
                "extracted",
                NOW,
                null
        );
    }

    private AssetRepository.AssetDetailRow detailRow(
            UUID assetId,
            String contentHash,
            String mediaType,
            List<AssetRepository.AssetFileRow> files
    ) {
        AssetRepository.AssetFileRow first = files.get(0);
        return new AssetRepository.AssetDetailRow(
                assetId,
                contentHash,
                "confirmed",
                mediaType,
                "available",
                1,
                new AssetMetadataResponse(first.capturedAt(), first.width(), first.height(), first.fileExtension(),
                        first.mimeType(), first.extractionStatus(), first.extractedAt(), first.errorMessage()),
                files.stream().map(row -> new AssetFileOccurrenceResponse(
                        row.fileId(),
                        row.libraryId(),
                        row.libraryName(),
                        row.path(),
                        row.folderPath(),
                        row.fileName(),
                        row.sizeBytes(),
                        row.modifiedAt(),
                        row.status()
                )).toList()
        );
    }

    private static final class FakeAssetRepository extends AssetRepository {
        private AssetSearchCriteria lastCriteria;
        private BrowseRows browseRows = new BrowseRows(List.of(), 0);
        private BrowseRows tagBrowseRows = new BrowseRows(List.of(), 0);
        private List<LibraryRootRow> libraryRoots = List.of();
        private List<FolderRow> folderRows = List.of();
        private List<MetadataCandidateRow> metadataCandidates = List.of();
        private final List<MetadataUpdate> metadataUpdates = new ArrayList<>();
        private final Map<UUID, String> searchTextByAsset = new LinkedHashMap<>();
        private final List<UUID> searchUpserts = new ArrayList<>();
        private Set<UUID> starredIds = Set.of();
        private boolean canReadAsset = true;
        private boolean canReadAssetInLibrary = true;
        private UUID lastBrowsedTag;
        private int lastTagPage;
        private int lastTagPageSize;
        private UUID lastCanReadUserId;
        private UUID lastCanReadAssetId;
        private boolean lastCanReadAdmin;
        private final Map<UUID, AssetDetailRow> details = new LinkedHashMap<>();

        private FakeAssetRepository() {
            super(null, null);
        }

        @Override
        List<LibraryRootRow> listLibraryRoots(UUID userId, boolean admin, UUID libraryId) {
            return libraryRoots;
        }

        @Override
        List<FolderRow> listFolders(UUID userId, boolean admin, UUID libraryId) {
            return folderRows;
        }

        @Override
        BrowseRows browse(UUID userId, boolean admin, AssetSearchCriteria criteria) {
            lastCriteria = criteria;
            return browseRows;
        }

        @Override
        BrowseRows browseTagAssets(UUID userId, boolean admin, UUID tagId, int page, int pageSize) {
            lastBrowsedTag = tagId;
            lastTagPage = page;
            lastTagPageSize = pageSize;
            return tagBrowseRows;
        }

        @Override
        Set<UUID> starredAssetIds(UUID userId, Collection<UUID> assetIds) {
            return starredIds;
        }

        @Override
        boolean canReadAsset(UUID userId, boolean admin, UUID assetId) {
            lastCanReadUserId = userId;
            lastCanReadAdmin = admin;
            lastCanReadAssetId = assetId;
            return canReadAsset;
        }

        @Override
        boolean canReadAssetInLibrary(UUID userId, boolean admin, UUID assetId, UUID libraryId) {
            return canReadAssetInLibrary;
        }

        @Override
        Optional<AssetDetailRow> findAsset(UUID userId, boolean admin, UUID assetId) {
            return Optional.ofNullable(details.get(assetId));
        }

        @Override
        List<MetadataCandidateRow> listMetadataCandidates(int limit) {
            return metadataCandidates;
        }

        @Override
        void upsertMetadata(MetadataUpdate update) {
            metadataUpdates.add(update);
        }

        @Override
        String searchableTextForAsset(UUID assetId) {
            return searchTextByAsset.get(assetId);
        }

        @Override
        void upsertSearchDocument(UUID assetId, String searchableText, OffsetDateTime now) {
            searchUpserts.add(assetId);
        }
    }

    private static final class FakeThumbnailService extends ThumbnailService {
        private final Map<String, ThumbnailBrowseSummary> summaries = new LinkedHashMap<>();
        private ResponseStatusException previewException;

        private FakeThumbnailService() {
            super(null, new StorageProperties());
        }

        @Override
        public Map<String, ThumbnailBrowseSummary> browseSummaries(List<String> contentHashes) {
            return summaries;
        }

        @Override
        public ThumbnailResponseResource previewThumbnail(AssetDetailResponse asset) {
            if (previewException != null) {
                throw previewException;
            }
            return new ThumbnailResponseResource(new ByteArrayResource(new byte[]{1}), 1, "image/jpeg", "etag", NOW);
        }

        @Override
        public ThumbnailResponseResource tinyThumbnail(AssetDetailResponse asset) {
            return new ThumbnailResponseResource(new ByteArrayResource(new byte[]{1}), 1, "image/jpeg", "tiny", NOW);
        }

        @Override
        public ThumbnailResponseResource gridThumbnail(AssetDetailResponse asset) {
            return new ThumbnailResponseResource(new ByteArrayResource(new byte[]{1}), 1, "image/jpeg", "grid", NOW);
        }
    }

    private static final class FakeTagRepository extends TagRepository {
        private List<AssetTagResponse> tags = List.of();
        private UUID lastUserId;

        private FakeTagRepository() {
            super(null);
        }

        @Override
        public List<AssetTagResponse> listAssetTags(UUID assetId, UUID ownerUserId) {
            lastUserId = ownerUserId;
            return tags;
        }
    }
}
