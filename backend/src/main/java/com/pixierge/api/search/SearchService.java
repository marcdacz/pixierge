package com.pixierge.api.search;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static com.pixierge.api.libraries.LibraryConstants.PERMISSION_LIBRARY_ADMIN;

@Service
public class SearchService {
    private static final int DEFAULT_SUGGESTION_LIMIT = 8;
    private static final int MAX_SUGGESTION_LIMIT = 25;

    private final SearchParser parser;
    private final SearchRepository repository;

    public SearchService(SearchParser parser, SearchRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    public SearchParseResponse parse(String query) {
        return parser.parseResponse(query);
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> suggest(
            AuthenticatedUser user,
            String fieldSyntax,
            String partial,
            Integer requestedLimit
    ) {
        SearchField field = SearchField.fromSyntax(fieldSyntax == null ? "" : fieldSyntax.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown search field"));
        int limit = Math.min(MAX_SUGGESTION_LIMIT, Math.max(1,
                requestedLimit == null ? DEFAULT_SUGGESTION_LIMIT : requestedLimit));
        return repository.suggest(field, partial, limit, user.id(),
                user.permissions().contains(PERMISSION_LIBRARY_ADMIN));
    }
}
