package com.pixierge.api.search;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SearchParser {

    private static final Set<String> IS_VALUES = Set.of("available", "missing", "duplicate", "starred");
    private static final Set<SearchField> DATE_FIELDS = EnumSet.of(SearchField.AFTER, SearchField.BEFORE, SearchField.ON);
    private static final Set<SearchField> COMMA_VALUE_FIELDS = EnumSet.of(
            SearchField.LIBRARY,
            SearchField.ALBUM,
            SearchField.TAG,
            SearchField.EXTENSION
    );

    public SearchParseResponse parseResponse(String source) {
        ParseResult result = parseInternal(source == null ? "" : source);
        List<SearchParseResponse.Clause> clauses = result.clauses().stream()
                .map(clause -> new SearchParseResponse.Clause(
                        clause.field().syntax(),
                        clause.value(),
                        clause.negated(),
                        clause.start(),
                        clause.end(),
                        (clause.negated() ? "Not " : "") + clause.field().syntax() + ": " + clause.value()
                ))
                .toList();
        return new SearchParseResponse(
                result.source(),
                result.freeText(),
                clauses,
                result.errors(),
                result.errors().isEmpty()
        );
    }

    public SearchQuery parse(String source) {
        if (source == null || source.isBlank()) {
            return SearchQuery.empty();
        }
        ParseResult result = parseInternal(source == null ? "" : source);
        if (!result.errors().isEmpty()) {
            throw new SearchValidationException(result.errors());
        }
        return new SearchQuery(result.source(), result.freeText(), result.clauses());
    }

    private ParseResult parseInternal(String source) {
        List<SearchClause> clauses = new ArrayList<>();
        List<SearchParseResponse.Error> errors = new ArrayList<>();
        List<String> freeText = new ArrayList<>();
        Set<SearchField> seenSingleFields = EnumSet.noneOf(SearchField.class);

        int cursor = 0;
        while (cursor < source.length()) {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= source.length()) {
                break;
            }
            Token token = readToken(source, cursor);
            cursor = token.end();
            if (token.error() != null) {
                errors.add(new SearchParseResponse.Error("MALFORMED_TOKEN", token.error(), token.start(), token.end()));
                continue;
            }

            String raw = token.value();
            boolean negated = raw.startsWith("-") && raw.length() > 1;
            String candidate = negated ? raw.substring(1) : raw;
            int colon = candidate.indexOf(':');
            if (colon < 0) {
                freeText.add(raw);
                continue;
            }

            String fieldSyntax = candidate.substring(0, colon);
            String value = candidate.substring(colon + 1);
            SearchField field = SearchField.fromSyntax(fieldSyntax).orElse(null);
            if (field == null) {
                String message = switch (fieldSyntax.toLowerCase(Locale.ROOT)) {
                    case "person", "object" -> "Search field '" + fieldSyntax + "' is unavailable until a plugin provides it";
                    default -> "Unknown search field '" + fieldSyntax + "'";
                };
                errors.add(new SearchParseResponse.Error("UNKNOWN_FIELD", message, token.start(), token.end()));
                continue;
            }
            if (value.isBlank() || value.startsWith(":")) {
                errors.add(new SearchParseResponse.Error(
                        "MISSING_VALUE", "Search field '" + field.syntax() + "' requires a value", token.start(), token.end()));
                continue;
            }
            if (!field.repeatable() && !seenSingleFields.add(field)) {
                errors.add(new SearchParseResponse.Error(
                        "REPEATED_FIELD", "Search field '" + field.syntax() + "' can only be used once", token.start(), token.end()));
                continue;
            }
            List<String> values = splitCommaValues(field, value);
            if (values.isEmpty()) {
                errors.add(new SearchParseResponse.Error(
                        "MISSING_VALUE", "Search field '" + field.syntax() + "' requires a value", token.start(), token.end()));
                continue;
            }
            boolean invalid = false;
            for (String part : values) {
                if (DATE_FIELDS.contains(field)) {
                    try {
                        LocalDate.parse(part);
                    } catch (DateTimeParseException exception) {
                        errors.add(new SearchParseResponse.Error(
                                "INVALID_DATE", "Use an ISO date such as 2026-07-11", token.start(), token.end()));
                        invalid = true;
                        break;
                    }
                }
                if (field == SearchField.IS && !IS_VALUES.contains(part.toLowerCase(Locale.ROOT))) {
                    errors.add(new SearchParseResponse.Error(
                            "INVALID_VALUE", "Supported is: values are available, missing, duplicate, and starred",
                            token.start(), token.end()));
                    invalid = true;
                    break;
                }
            }
            if (invalid) {
                continue;
            }
            for (String part : values) {
                clauses.add(new SearchClause(field, part, negated, token.start(), token.end()));
            }
        }
        return new ParseResult(source, String.join(" ", freeText).trim(), clauses, errors);
    }

    private List<String> splitCommaValues(SearchField field, String value) {
        if (!COMMA_VALUE_FIELDS.contains(field) || !value.contains(",")) {
            return List.of(value);
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    private Token readToken(String source, int start) {
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        int cursor = start;
        while (cursor < source.length()) {
            char character = source.charAt(cursor);
            if (escaped) {
                value.append(character);
                escaped = false;
                cursor++;
                continue;
            }
            if (character == '\\' && quoted) {
                escaped = true;
                cursor++;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                cursor++;
                continue;
            }
            if (!quoted && Character.isWhitespace(character)) {
                break;
            }
            value.append(character);
            cursor++;
        }
        if (escaped || quoted) {
            return new Token(start, cursor, value.toString(), "Unterminated quoted value");
        }
        return new Token(start, cursor, value.toString(), null);
    }

    private record Token(int start, int end, String value, String error) {
    }

    private record ParseResult(
            String source,
            String freeText,
            List<SearchClause> clauses,
            List<SearchParseResponse.Error> errors
    ) {
    }
}
