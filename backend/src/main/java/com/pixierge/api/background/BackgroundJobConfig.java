package com.pixierge.api.background;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
}
