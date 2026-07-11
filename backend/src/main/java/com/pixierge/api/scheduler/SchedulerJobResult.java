package com.pixierge.api.scheduler;

public record SchedulerJobResult(String summaryJson) {
    public static SchedulerJobResult empty() {
        return new SchedulerJobResult(null);
    }
}
