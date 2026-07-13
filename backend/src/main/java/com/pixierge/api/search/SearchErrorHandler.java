package com.pixierge.api.search;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class SearchErrorHandler {
    @ExceptionHandler(SearchValidationException.class)
    ResponseEntity<SearchErrorResponse> invalidSearch(SearchValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SearchErrorResponse("INVALID_SEARCH", "Search query is invalid", exception.errors()));
    }

    record SearchErrorResponse(String code, String message, List<SearchParseResponse.Error> errors) {
    }
}
