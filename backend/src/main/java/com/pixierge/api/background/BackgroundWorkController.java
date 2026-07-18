package com.pixierge.api.background;

import com.pixierge.api.filesystem.FilesystemWatcherHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BackgroundWorkController {

    private final BackgroundJobService backgroundJobService;
    private final FilesystemWatcherHealth watcherHealth;

    BackgroundWorkController(BackgroundJobService backgroundJobService, FilesystemWatcherHealth watcherHealth) {
        this.backgroundJobService = backgroundJobService;
        this.watcherHealth = watcherHealth;
    }

    @GetMapping("/api/admin/background/health")
    BackgroundWorkHealthResponse health() {
        return new BackgroundWorkHealthResponse(
                backgroundJobService.summarizeByTypeAndStatus(),
                backgroundJobService.latestProblemJobs(10),
                watcherHealth.snapshot()
        );
    }
}
