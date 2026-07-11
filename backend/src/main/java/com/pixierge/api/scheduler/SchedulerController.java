package com.pixierge.api.scheduler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @GetMapping("/api/admin/scheduler/jobs")
    List<SchedulerJobResponse> listJobs() {
        return schedulerService.listJobs();
    }

    @GetMapping("/api/admin/scheduler/jobs/{jobId}")
    SchedulerJobResponse getJob(@PathVariable UUID jobId) {
        return schedulerService.getJob(jobId);
    }

    @PatchMapping("/api/admin/scheduler/jobs/{jobId}")
    SchedulerJobResponse updateJob(@PathVariable UUID jobId, @RequestBody UpdateSchedulerJobRequest request) {
        return schedulerService.updateJob(jobId, request);
    }

    @PostMapping("/api/admin/scheduler/jobs/{jobId}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SchedulerJobRunResponse runNow(@PathVariable UUID jobId) {
        return schedulerService.runNow(jobId);
    }

}
