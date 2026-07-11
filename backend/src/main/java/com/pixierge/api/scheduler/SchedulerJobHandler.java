package com.pixierge.api.scheduler;

@FunctionalInterface
public interface SchedulerJobHandler {

    SchedulerJobResult execute(SchedulerJobRecord job) throws Exception;
}
