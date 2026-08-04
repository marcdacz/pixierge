package com.pixierge.api.albums;

import com.pixierge.api.assets.AssetBrowseResponse;
import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AlbumController {
    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping("/api/albums")
    List<AlbumSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) String scope) {
        return albumService.list(user, scope);
    }

    @PostMapping("/api/albums")
    @ResponseStatus(HttpStatus.CREATED)
    AlbumSummaryResponse create(@RequestBody CreateAlbumRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return albumService.create(request, user);
    }

    @GetMapping("/api/albums/{albumId}")
    AlbumSummaryResponse get(@PathVariable UUID albumId, @AuthenticationPrincipal AuthenticatedUser user) {
        return albumService.get(albumId, user);
    }

    @PatchMapping("/api/albums/{albumId}")
    AlbumSummaryResponse update(@PathVariable UUID albumId, @RequestBody UpdateAlbumRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return albumService.update(albumId, request, user);
    }

    @DeleteMapping("/api/albums/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID albumId, @AuthenticationPrincipal AuthenticatedUser user) {
        albumService.delete(albumId, user);
    }

    @GetMapping("/api/albums/{albumId}/assets")
    AssetBrowseResponse assets(@PathVariable UUID albumId, @AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer pageSize) {
        return albumService.browseAssets(albumId, user, page, pageSize);
    }

    @GetMapping("/api/starred")
    AlbumSummaryResponse starred(@AuthenticationPrincipal AuthenticatedUser user) {
        return albumService.getOrCreateStarred(user);
    }

    @GetMapping("/api/starred/assets")
    AssetBrowseResponse starredAssets(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer pageSize) {
        return albumService.browseStarredAssets(user, page, pageSize);
    }

    @PostMapping("/api/album-items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addItems(@RequestBody AddAlbumItemsRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        albumService.addItems(request, user);
    }

    @DeleteMapping("/api/albums/{albumId}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItems(@PathVariable UUID albumId, @RequestBody DeleteAlbumItemsRequest request,
                     @AuthenticationPrincipal AuthenticatedUser user) {
        albumService.deleteItems(albumId, request, user);
    }

    @GetMapping("/api/albums/{albumId}/members") List<AlbumRepository.AlbumMemberRecord> members(@PathVariable UUID albumId, @AuthenticationPrincipal AuthenticatedUser user) { return albumService.members(albumId, user); }
    @GetMapping("/api/albums/{albumId}/members/candidates") List<AlbumRepository.AlbumMemberRecord> memberCandidates(@PathVariable UUID albumId, @AuthenticationPrincipal AuthenticatedUser user) { return albumService.memberCandidates(albumId, user); }
    @PostMapping("/api/albums/{albumId}/members") AlbumRepository.AlbumMemberRecord addMember(@PathVariable UUID albumId, @RequestBody UpsertAlbumMemberRequest request, @AuthenticationPrincipal AuthenticatedUser user) { return albumService.addMember(albumId, request, user); }
    @DeleteMapping("/api/albums/{albumId}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) void removeMember(@PathVariable UUID albumId, @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser user) { albumService.removeMember(albumId, userId, user); }
}
