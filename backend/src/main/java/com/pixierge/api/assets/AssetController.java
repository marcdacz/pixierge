package com.pixierge.api.assets;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.pixierge.api.assets.AssetConstants.DEFAULT_PAGE_SIZE_PARAM;

@RestController
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/api/library-tree")
    LibraryTreeResponse libraryTree(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID libraryId
    ) {
        return assetService.libraryTree(user, libraryId);
    }

    @GetMapping("/api/assets")
    AssetBrowseResponse browse(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID libraryId,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false, defaultValue = "true") Boolean includeDescendants,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Boolean duplicatesOnly,
            @RequestParam(required = false) List<UUID> tag,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = DEFAULT_PAGE_SIZE_PARAM) Integer pageSize
    ) {
        return assetService.browse(
                user,
                libraryId,
                folder,
                includeDescendants,
                q,
                availability,
                fileType,
                duplicatesOnly,
                tag,
                page,
                pageSize
        );
    }

    @GetMapping("/api/assets/{assetId}")
    AssetDetailResponse getAsset(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assetId) {
        return assetService.getAsset(user, assetId);
    }

    @GetMapping("/api/assets/{assetId}/file")
    ResponseEntity<?> file(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assetId) {
        AssetFileResource file = assetService.file(user, assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(file.resource());
    }

    @GetMapping("/api/assets/{assetId}/thumbnail")
    ResponseEntity<?> thumbnail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID assetId,
            @RequestParam(required = false, defaultValue = "grid") String size
    ) {
        ThumbnailResponseResource thumbnail = assetService.thumbnail(user, assetId, size);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(thumbnail.contentType()))
                .contentLength(thumbnail.contentLength())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(24)).cachePrivate())
                .header(HttpHeaders.ETAG, thumbnail.etag())
                .lastModified(thumbnail.lastModified().toInstant().toEpochMilli())
                .body(thumbnail.resource());
    }

    @GetMapping("/api/assets/{assetId}/preview")
    ResponseEntity<?> preview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assetId) {
        ThumbnailResponseResource preview = assetService.preview(user, assetId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.contentType()))
                .contentLength(preview.contentLength())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(24)).cachePrivate())
                .lastModified(preview.lastModified().toInstant().toEpochMilli());
        if (preview.etag() != null) {
            response.header(HttpHeaders.ETAG, preview.etag());
        }
        return response.body(preview.resource());
    }

    @PostMapping("/api/admin/thumbnails/rebuild-missing")
    AdminBatchActionResponse rebuildMissingThumbnails() {
        return assetService.rebuildMissingThumbnails();
    }

    @PostMapping("/api/admin/thumbnails/purge-stale")
    AdminBatchActionResponse purgeStaleThumbnails() {
        return assetService.purgeStaleThumbnails();
    }

    @PostMapping("/api/assets/metadata/backfill")
    AdminBatchActionResponse backfillMetadata() {
        return assetService.backfillMetadata();
    }
}
