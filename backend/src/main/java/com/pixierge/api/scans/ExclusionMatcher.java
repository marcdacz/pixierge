package com.pixierge.api.scans;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

final class ExclusionMatcher {

    private final List<Pattern> patterns;

    ExclusionMatcher(List<String> patterns) {
        this.patterns = patterns.stream()
                .map(ExclusionMatcher::toRegex)
                .map(Pattern::compile)
                .toList();
    }

    boolean matches(Path root, Path candidate) {
        String relative = root.relativize(candidate).toString().replace('\\', '/');
        return patterns.stream().anyMatch(pattern -> pattern.matcher(relative).matches());
    }

    private static String toRegex(String glob) {
        String normalized = glob.trim().replace('\\', '/');
        StringBuilder regex = new StringBuilder();
        if (normalized.startsWith("**/")) {
            regex.append("(?:.*/)?");
            normalized = normalized.substring(3);
        }

        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '*') {
                if (index + 1 < normalized.length() && normalized.charAt(index + 1) == '*') {
                    regex.append(".*");
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                regex.append('\\').append(current);
            } else {
                regex.append(current);
            }
        }
        return regex.toString();
    }
}
