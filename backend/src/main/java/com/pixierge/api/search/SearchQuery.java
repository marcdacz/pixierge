package com.pixierge.api.search;

import java.util.List;

public record SearchQuery(String source, String freeText, List<SearchClause> clauses) {

    public SearchQuery {
        source = source == null ? "" : source;
        freeText = freeText == null ? "" : freeText;
        clauses = List.copyOf(clauses);
    }

    public static SearchQuery empty() {
        return new SearchQuery("", "", List.of());
    }
}
