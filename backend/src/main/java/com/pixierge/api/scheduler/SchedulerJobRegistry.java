package com.pixierge.api.scheduler;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SchedulerJobRegistry {

    private final Map<String, SchedulerJobDefinition> definitions = new LinkedHashMap<>();

    public SchedulerJobRegistry(List<SchedulerJobDefinition> definitions) {
        for (SchedulerJobDefinition definition : definitions) {
            if (this.definitions.put(definition.jobKey(), definition) != null) {
                throw new IllegalStateException("Duplicate scheduler job key: " + definition.jobKey());
            }
        }
    }

    public Collection<SchedulerJobDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<SchedulerJobDefinition> find(String jobKey) {
        return Optional.ofNullable(definitions.get(jobKey));
    }

    public SchedulerJobHandler requireHandler(String jobKey) {
        return find(jobKey)
                .map(SchedulerJobDefinition::handler)
                .orElseThrow(() -> new IllegalStateException("No handler registered for job key: " + jobKey));
    }
}
