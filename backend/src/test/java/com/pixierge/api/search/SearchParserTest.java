package com.pixierge.api.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class SearchParserTest {
    private final SearchParser parser = new SearchParser();

    @Test
    void parsesFreeTextQuotedValuesNegationAndDates() {
        SearchQuery query = parser.parse("beach holiday album:\"Japan 2025\" -tag:private after:2026-01-01");

        assertThat(query.freeText()).isEqualTo("beach holiday");
        assertThat(query.clauses()).extracting(SearchClause::field)
                .containsExactly(SearchField.ALBUM, SearchField.TAG, SearchField.AFTER);
        assertThat(query.clauses().get(0).value()).isEqualTo("Japan 2025");
        assertThat(query.clauses().get(1).negated()).isTrue();
        assertThat(query.clauses().get(2).value()).isEqualTo("2026-01-01");
    }

    @Test
    void acceptsRepeatedRelationStateAndExtensionFieldsButRejectsRepeatedScalarFields() {
        assertThat(parser.parse(
                "library:Events library:Japan tag:family tag:holiday is:starred -is:missing extension:jpg,png"
        ).clauses()).hasSize(8);
        assertThat(parser.parse("extension:jpg extension:png").clauses()).hasSize(2);
        assertThat(parser.parse("library:Events,Japan album:Summer,Winter tag:family,holiday").clauses())
                .extracting(SearchClause::field, SearchClause::value)
                .containsExactly(
                        tuple(SearchField.LIBRARY, "Events"),
                        tuple(SearchField.LIBRARY, "Japan"),
                        tuple(SearchField.ALBUM, "Summer"),
                        tuple(SearchField.ALBUM, "Winter"),
                        tuple(SearchField.TAG, "family"),
                        tuple(SearchField.TAG, "holiday")
                );

        SearchParseResponse response = parser.parseResponse("camera:sony camera:canon");
        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("REPEATED_FIELD");
            assertThat(error.start()).isEqualTo(12);
            assertThat(error.end()).isEqualTo(24);
        });
    }

    @Test
    void reportsUnknownPluginFieldsMalformedQuotesAndInvalidDates() {
        SearchParseResponse unavailable = parser.parseResponse("person:Alice");
        SearchParseResponse malformed = parser.parseResponse("album:\"Japan 2025");
        SearchParseResponse invalidDate = parser.parseResponse("on:11/07/2026");

        assertThat(unavailable.errors().getFirst().message()).contains("unavailable until a plugin");
        assertThat(malformed.errors().getFirst().code()).isEqualTo("MALFORMED_TOKEN");
        assertThat(invalidDate.errors().getFirst().code()).isEqualTo("INVALID_DATE");
        assertThatThrownBy(() -> parser.parse("camera::sony"))
                .isInstanceOf(SearchValidationException.class)
                .hasMessageContaining("requires a value");
    }
}
