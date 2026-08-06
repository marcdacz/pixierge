package com.pixierge.api.background;

record BackgroundWorkConfigResponse(
    int maxConcurrentJobs,
    int maxConcurrentMetadataJobs,
    int identityBatchSize,
    int claimBatchSize,
    long pollIntervalMs) {
  BackgroundWorkConfigResponse(
      int maxConcurrentJobs, int identityBatchSize, int claimBatchSize, long pollIntervalMs) {
    this(maxConcurrentJobs, maxConcurrentJobs, identityBatchSize, claimBatchSize, pollIntervalMs);
  }
}
