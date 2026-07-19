package com.pixierge.api.background;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundFileActivityPagerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 19, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void pageZeroPrependsActiveRowsAndCountsThemTowardPageSize() {
        List<BackgroundFileActivitySummary> active = List.of(
                active("a.jpg", "processing"),
                active("b.jpg", "pending")
        );
        AtomicReference<Integer> requestedOffset = new AtomicReference<>();
        AtomicReference<Integer> requestedLimit = new AtomicReference<>();

        BackgroundFileActivityPage page = BackgroundFileActivityPager.page(
                active,
                0,
                3,
                (offset, limit) -> {
                    requestedOffset.set(offset);
                    requestedLimit.set(limit);
                    return new BackgroundActivityRepository.PersistedFileActivityPage(
                            List.of(row("/photos/c.jpg", "added", NOW.minusMinutes(1))),
                            5
                    );
                }
        );

        assertThat(requestedOffset.get()).isZero();
        assertThat(requestedLimit.get()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.pageSize()).isEqualTo(3);
        assertThat(page.totalCount()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(BackgroundFileActivitySummary::fileName)
                .containsExactly("a.jpg", "b.jpg", "c.jpg");
    }

    @Test
    void laterPagesSkipActiveRowsAndOffsetPersistedResults() {
        List<BackgroundFileActivitySummary> active = List.of(
                active("a.jpg", "processing"),
                active("b.jpg", "pending")
        );
        AtomicReference<Integer> requestedOffset = new AtomicReference<>();

        BackgroundFileActivityPage page = BackgroundFileActivityPager.page(
                active,
                1,
                2,
                (offset, limit) -> {
                    requestedOffset.set(offset);
                    return new BackgroundActivityRepository.PersistedFileActivityPage(
                            List.of(
                                    row("/photos/c.jpg", "added", NOW.minusMinutes(2)),
                                    row("/photos/d.jpg", "modified", NOW.minusMinutes(3))
                            ),
                            4
                    );
                }
        );

        assertThat(requestedOffset.get()).isEqualTo(0);
        assertThat(page.totalCount()).isEqualTo(6);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(BackgroundFileActivitySummary::fileName)
                .containsExactly("c.jpg", "d.jpg");
    }

    @Test
    void matchesFiltersActiveRowsByQueryStatusAndDate() {
        BackgroundFileActivitySummary item = active("/photos/family/lake.jpg", "processing");

        assertThat(BackgroundFileActivityPager.matches(item, "lake", List.of("processing"), null, null)).isTrue();
        assertThat(BackgroundFileActivityPager.matches(item, "mountain", null, null, null)).isFalse();
        assertThat(BackgroundFileActivityPager.matches(item, null, List.of("failed"), null, null)).isFalse();
        assertThat(BackgroundFileActivityPager.matches(
                item,
                null,
                null,
                NOW.plusMinutes(1),
                null
        )).isFalse();
        assertThat(BackgroundFileActivityPager.matches(
                item,
                null,
                List.of("processing", "added"),
                NOW.minusMinutes(1),
                NOW.plusMinutes(1)
        )).isTrue();
    }

    private static BackgroundFileActivitySummary active(String pathOrName, String status) {
        String path = pathOrName.contains("/") ? pathOrName : "/photos/" + pathOrName;
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return new BackgroundFileActivitySummary(
                path,
                fileName,
                status,
                UUID.randomUUID(),
                "identity batch 1",
                NOW,
                null
        );
    }

    private static BackgroundFileActivityRow row(String path, String result, OffsetDateTime observedAt) {
        return new BackgroundFileActivityRow(path, result, observedAt, null);
    }
}
