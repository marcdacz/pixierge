package com.pixierge.api.search;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search/parse")
    SearchParseResponse parse(@RequestParam(required = false, defaultValue = "") String q) {
        return searchService.parse(q);
    }

    @GetMapping("/api/search/suggestions")
    List<SearchSuggestionResponse> suggestions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String field,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) Integer limit
    ) {
        return searchService.suggest(user, field, q, limit);
    }
}
