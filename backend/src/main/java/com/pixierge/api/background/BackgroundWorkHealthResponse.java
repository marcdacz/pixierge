package com.pixierge.api.background;

import com.pixierge.api.filesystem.FilesystemWatcherHealthSnapshot;

import java.util.List;

record BackgroundWorkHealthResponse(
        List<BackgroundJobStatusSummary> queues,
        List<BackgroundJobProblemSummary> recentProblems,
        FilesystemWatcherHealthSnapshot watcher
) {
}
