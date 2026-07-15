package com.pixierge.api.libraries;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/api/libraries")
    List<LibraryResponse> listLibraries() {
        return libraryService.listLibraries();
    }

    @PostMapping("/api/libraries")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryResponse createLibrary(@RequestBody CreateLibraryRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return libraryService.createLibrary(request, user.id());
    }

    @PatchMapping("/api/libraries/{libraryId}")
    LibraryResponse updateLibrary(@PathVariable UUID libraryId, @RequestBody UpdateLibraryRequest request) {
        return libraryService.updateLibrary(libraryId, request);
    }

    @PostMapping("/api/libraries/{libraryId}/folders/rename")
    RenameFolderResponse renameFolder(@PathVariable UUID libraryId, @RequestBody RenameFolderRequest request) {
        return libraryService.renameFolder(libraryId, request);
    }

    @PostMapping("/api/libraries/{libraryId}/roots")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryResponse addRoot(
            @PathVariable UUID libraryId,
            @RequestBody AddLibraryRootRequest request
    ) {
        return libraryService.addRoot(libraryId, request);
    }

    @DeleteMapping("/api/libraries/{libraryId}/roots/{rootId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoot(@PathVariable UUID libraryId, @PathVariable UUID rootId) {
        libraryService.deleteRoot(libraryId, rootId);
    }

    @PostMapping("/api/libraries/{libraryId}/archive")
    LibraryResponse archiveLibrary(@PathVariable UUID libraryId) {
        return libraryService.archiveLibrary(libraryId);
    }

    @PostMapping("/api/libraries/{libraryId}/restore")
    LibraryResponse restoreLibrary(@PathVariable UUID libraryId) {
        return libraryService.restoreLibrary(libraryId);
    }

    @PostMapping("/api/libraries/{libraryId}/exclusion-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryResponse addExclusionPattern(
            @PathVariable UUID libraryId,
            @RequestBody AddExclusionPatternRequest request
    ) {
        return libraryService.addExclusionPattern(libraryId, request);
    }

    @DeleteMapping("/api/libraries/{libraryId}/exclusion-patterns/{patternId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExclusionPattern(@PathVariable UUID libraryId, @PathVariable UUID patternId) {
        libraryService.deleteExclusionPattern(libraryId, patternId);
    }

    @GetMapping("/api/settings/global-exclusion-patterns")
    List<LibraryExclusionPatternResponse> listGlobalExclusionPatterns() {
        return libraryService.listGlobalExclusionPatterns();
    }

    @PostMapping("/api/settings/global-exclusion-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryExclusionPatternResponse addGlobalExclusionPattern(@RequestBody AddExclusionPatternRequest request) {
        return libraryService.addGlobalExclusionPattern(request);
    }

    @DeleteMapping("/api/settings/global-exclusion-patterns/{patternId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGlobalExclusionPattern(@PathVariable UUID patternId) {
        libraryService.deleteGlobalExclusionPattern(patternId);
    }
}
