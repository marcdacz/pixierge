package com.pixierge.api.background;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import com.pixierge.api.scans.ScanJobTypes;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import org.springframework.core.task.TaskExecutor;

@Configuration
class BackgroundJobConfig {

    @Bean
    ThreadPoolTaskExecutor backgroundJobTaskExecutor(
            @Value("${pixierge.background-jobs.max-concurrent-jobs:2}") int maxConcurrentJobs
    ) {
        int workerCount = Math.max(1, maxConcurrentJobs);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(workerCount);
        executor.setThreadNamePrefix("pixierge-job-");
        executor.initialize();
        return executor;
    }

    @Bean
    ThreadPoolTaskExecutor metadataBackgroundJobTaskExecutor(
            @Value("${pixierge.background-jobs.max-concurrent-metadata-jobs:2}") int maxConcurrentMetadataJobs
    ) {
        int workerCount = Math.max(1, maxConcurrentMetadataJobs);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(workerCount);
        executor.setThreadNamePrefix("pixierge-metadata-");
        executor.initialize();
        return executor;
    }

    @Bean
    BackgroundJobWorker backgroundJobWorker(
            BackgroundJobService jobService,
            List<BackgroundJobHandler> handlers,
            @Qualifier("backgroundJobTaskExecutor") TaskExecutor taskExecutor,
            @Value("${pixierge.background-jobs.max-concurrent-jobs:2}") int maxConcurrentJobs,
            ApplicationEventPublisher eventPublisher
    ) {
        return new BackgroundJobWorker(
                jobService, handlers, taskExecutor, maxConcurrentJobs, null,
                ScanJobTypes.ASSET_METADATA_BACKFILL, eventPublisher
        );
    }

    @Bean
    BackgroundJobWorker metadataBackgroundJobWorker(
            BackgroundJobService jobService,
            List<BackgroundJobHandler> handlers,
            @Qualifier("metadataBackgroundJobTaskExecutor") TaskExecutor taskExecutor,
            @Value("${pixierge.background-jobs.max-concurrent-metadata-jobs:2}") int maxConcurrentMetadataJobs,
            ApplicationEventPublisher eventPublisher
    ) {
        return new BackgroundJobWorker(
                jobService, handlers, taskExecutor, maxConcurrentMetadataJobs,
                ScanJobTypes.ASSET_METADATA_BACKFILL, null, eventPublisher
        );
    }
}
