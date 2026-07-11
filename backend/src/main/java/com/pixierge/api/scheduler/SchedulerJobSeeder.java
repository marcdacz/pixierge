package com.pixierge.api.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class SchedulerJobSeeder {

    private static final Logger log = LoggerFactory.getLogger(SchedulerJobSeeder.class);
    private static final String OWNER_CORE = "core";

    private final SchedulerJobRegistry registry;
    private final SchedulerRepository repository;

    public SchedulerJobSeeder(SchedulerJobRegistry registry, SchedulerRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedJobs() {
        OffsetDateTime now = OffsetDateTime.now();
        for (SchedulerJobDefinition definition : registry.definitions()) {
            repository.findJobByKey(definition.jobKey()).ifPresentOrElse(
                    existing -> repository.updateJobDefinitionMetadata(
                            existing.id(),
                            definition.displayName(),
                            definition.description(),
                            definition.timeoutSeconds(),
                            definition.concurrencyKey()
                    ),
                    () -> {
                        OffsetDateTime nextRun = definition.enabledByDefault()
                                ? CronSupport.nextRunAt(
                                definition.defaultCronExpression(),
                                definition.defaultTimezone(),
                                now
                        )
                                : null;
                        repository.insertJob(
                                definition.jobKey(),
                                definition.displayName(),
                                definition.description(),
                                OWNER_CORE,
                                definition.enabledByDefault(),
                                definition.defaultCronExpression(),
                                definition.defaultTimezone(),
                                nextRun,
                                definition.timeoutSeconds(),
                                definition.concurrencyKey()
                        );
                        log.info("Seeded scheduler job {}", definition.jobKey());
                    }
            );
        }
    }
}
