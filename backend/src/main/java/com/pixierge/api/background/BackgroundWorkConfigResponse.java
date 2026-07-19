package com.pixierge.api.background;

record BackgroundWorkConfigResponse(
        int maxConcurrentJobs,
        int identityBatchSize,
        int claimBatchSize,
        long pollIntervalMs
) {
}
