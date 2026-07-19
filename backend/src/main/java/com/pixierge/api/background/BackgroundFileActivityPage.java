package com.pixierge.api.background;

import java.util.List;

record BackgroundFileActivityPage(
        List<BackgroundFileActivitySummary> items,
        int page,
        int pageSize,
        int totalCount,
        boolean hasNext
) {
}
