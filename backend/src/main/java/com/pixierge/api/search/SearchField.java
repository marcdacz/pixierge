package com.pixierge.api.search;

import java.util.Arrays;
import java.util.Optional;

public enum SearchField {
    LIBRARY("library", true),
    FOLDER("folder", false),
    ALBUM("album", true),
    TAG("tag", true),
    EXTENSION("extension", true),
    CAMERA("camera", false),
    AFTER("after", false),
    BEFORE("before", false),
    ON("on", false),
    IS("is", true);

    private final String syntax;
    private final boolean repeatable;

    SearchField(String syntax, boolean repeatable) {
        this.syntax = syntax;
        this.repeatable = repeatable;
    }

    public String syntax() {
        return syntax;
    }

    public boolean repeatable() {
        return repeatable;
    }

    public static Optional<SearchField> fromSyntax(String syntax) {
        return Arrays.stream(values()).filter(field -> field.syntax.equalsIgnoreCase(syntax)).findFirst();
    }
}
