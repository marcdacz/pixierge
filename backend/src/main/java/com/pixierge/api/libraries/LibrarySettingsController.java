package com.pixierge.api.libraries;

import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/settings")
public class LibrarySettingsController {

    private final LibraryService libraryService;

    public LibrarySettingsController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/global-exclusion-patterns")
    List<LibraryExclusionPatternResponse> listGlobalExclusionPatterns() {
        return libraryService.listGlobalExclusionPatterns();
    }

    @PostMapping("/global-exclusion-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryExclusionPatternResponse addGlobalExclusionPattern(@RequestBody AddGlobalExclusionPatternRequest request) {
        return libraryService.addGlobalExclusionPattern(request);
    }

    @DeleteMapping("/global-exclusion-patterns/{patternId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGlobalExclusionPattern(@PathVariable UUID patternId) {
        libraryService.deleteGlobalExclusionPattern(patternId);
    }
}
