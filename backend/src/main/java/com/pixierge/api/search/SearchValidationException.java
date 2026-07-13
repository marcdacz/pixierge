package com.pixierge.api.search;

import java.util.List;

public class SearchValidationException extends RuntimeException {
    private final List<SearchParseResponse.Error> errors;

    SearchValidationException(List<SearchParseResponse.Error> errors) {
        super(errors.isEmpty() ? "Invalid search query" : errors.getFirst().message());
        this.errors = List.copyOf(errors);
    }

    public List<SearchParseResponse.Error> errors() {
        return errors;
    }
}
