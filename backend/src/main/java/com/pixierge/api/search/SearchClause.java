package com.pixierge.api.search;

public record SearchClause(
        SearchField field,
        String value,
        boolean negated,
        int start,
        int end
) {
}
