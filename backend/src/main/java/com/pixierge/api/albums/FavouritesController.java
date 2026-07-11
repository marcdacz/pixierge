package com.pixierge.api.albums;

import com.pixierge.api.assets.AssetBrowseResponse;
import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FavouritesController {
    private final AlbumService albumService;

    public FavouritesController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @GetMapping("/api/favourites")
    AlbumSummaryResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return albumService.getOrCreateFavourites(user);
    }

    @GetMapping("/api/favourites/assets")
    AssetBrowseResponse assets(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer pageSize) {
        return albumService.browseFavouritesAssets(user, page, pageSize);
    }
}
