package com.pixierge.api.libraries;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    List<LibraryResponse> listLibraries() {
        return libraryService.listLibraries();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LibraryResponse createLibrary(@RequestBody CreateLibraryRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return libraryService.createLibrary(request, user.id());
    }

    @PostMapping("/{libraryId}/roots")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryResponse addRoot(
            @PathVariable UUID libraryId,
            @RequestBody AddLibraryRootRequest request
    ) {
        return libraryService.addRoot(libraryId, request);
    }

    @DeleteMapping("/{libraryId}/roots/{rootId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoot(@PathVariable UUID libraryId, @PathVariable UUID rootId) {
        libraryService.deleteRoot(libraryId, rootId);
    }
}
