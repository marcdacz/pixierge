package com.pixierge.api.background;

import java.util.List;

record BackgroundWorkActivityResponse(
        List<BackgroundActivityJobSummary> jobs,
        List<BackgroundFileActivitySummary> files
) {
}
