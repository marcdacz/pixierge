package com.pixierge.api.tags;

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
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/api/tags")
    List<TagResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return tagService.list(user);
    }

    @PostMapping("/api/tags")
    @ResponseStatus(HttpStatus.CREATED)
    TagResponse create(@RequestBody CreateTagRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return tagService.create(request, user);
    }

    @PatchMapping("/api/tags/{tagId}")
    TagResponse rename(@PathVariable UUID tagId, @RequestBody UpdateTagRequest request,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        return tagService.rename(tagId, request, user);
    }

    @DeleteMapping("/api/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID tagId, @AuthenticationPrincipal AuthenticatedUser user) {
        tagService.delete(tagId, user);
    }

    @GetMapping("/api/tags/{tagId}/assets")
    AssetBrowseResponse assets(@PathVariable UUID tagId, @AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer pageSize) {
        return tagService.browseAssets(tagId, user, page, pageSize);
    }

    @PostMapping("/api/asset-tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void assign(@RequestBody AssignAssetTagsRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        tagService.addAssignments(request, user);
    }

    @DeleteMapping("/api/asset-tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unassign(@RequestBody DeleteAssetTagsRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        tagService.deleteAssignments(request, user);
    }
}
