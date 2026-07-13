package com.pixierge.api.search;

import java.util.List;

public record SearchParseResponse(
        String query,
        String freeText,
        List<Clause> clauses,
        List<Error> errors,
        boolean valid
) {
    public record Clause(String field, String value, boolean negated, int start, int end, String label) {
    }

    public record Error(String code, String message, int start, int end) {
    }
}
