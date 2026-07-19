package com.pixierge.api.background;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

final class BackgroundFileActivityPager {

    private BackgroundFileActivityPager() {
    }

    static BackgroundFileActivityPage page(
            List<BackgroundFileActivitySummary> activeMatches,
            int page,
            int pageSize,
            BiFunction<Integer, Integer, BackgroundActivityRepository.PersistedFileActivityPage> persistedPage
    ) {
        int normalizedPage = Math.max(0, page);
        int normalizedPageSize = Math.min(200, Math.max(1, pageSize));
        int activeCount = activeMatches.size();
        int globalOffset = normalizedPage * normalizedPageSize;

        List<BackgroundFileActivitySummary> items = new ArrayList<>(normalizedPageSize);
        if (globalOffset < activeCount) {
            int activeEnd = Math.min(activeCount, globalOffset + normalizedPageSize);
            items.addAll(activeMatches.subList(globalOffset, activeEnd));
        }

        int remaining = normalizedPageSize - items.size();
        int persistedOffset = Math.max(0, globalOffset - activeCount);
        BackgroundActivityRepository.PersistedFileActivityPage persisted =
                persistedPage.apply(persistedOffset, Math.max(remaining, 0));
        if (remaining > 0) {
            items.addAll(persisted.items().stream().map(BackgroundFileActivityPager::toSummary).toList());
        }

        int totalCount = activeCount + persisted.totalCount();
        boolean hasNext = (normalizedPage + 1L) * normalizedPageSize < totalCount;
        return new BackgroundFileActivityPage(List.copyOf(items), normalizedPage, normalizedPageSize, totalCount, hasNext);
    }

    static boolean matches(
            BackgroundFileActivitySummary item,
            String q,
            Collection<String> statuses,
            java.time.OffsetDateTime updatedFrom,
            java.time.OffsetDateTime updatedTo
    ) {
        if (statuses != null && !statuses.isEmpty()
                && statuses.stream().noneMatch(status -> status.equalsIgnoreCase(item.status()))) {
            return false;
        }
        if (q != null) {
            String haystack = ((item.path() == null ? "" : item.path()) + " " + item.fileName()).toLowerCase(Locale.ROOT);
            if (!haystack.contains(q)) {
                return false;
            }
        }
        if (updatedFrom != null && (item.updatedAt() == null || item.updatedAt().isBefore(updatedFrom))) {
            return false;
        }
        if (updatedTo != null && (item.updatedAt() == null || !item.updatedAt().isBefore(updatedTo))) {
            return false;
        }
        return true;
    }

    private static BackgroundFileActivitySummary toSummary(BackgroundFileActivityRow row) {
        return new BackgroundFileActivitySummary(
                row.path(),
                fileName(row.path()),
                row.result(),
                null,
                null,
                row.observedAt(),
                row.message()
        );
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "Unknown file";
        }
        java.nio.file.Path fileName = java.nio.file.Path.of(path).getFileName();
        return fileName == null ? path : fileName.toString();
    }
}
