package com.pixierge.api.scans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobHandler;
import com.pixierge.api.background.BackgroundJobRecord;
import org.springframework.stereotype.Component;

@Component
class ScanIdentityJobHandler implements BackgroundJobHandler {

    private final ScanService scanService;
    private final ObjectMapper objectMapper;

    ScanIdentityJobHandler(ScanService scanService, ObjectMapper objectMapper) {
        this.scanService = scanService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return ScanJobTypes.ASSET_IDENTITY_BACKFILL;
    }

    @Override
    public void handle(BackgroundJobRecord job) throws JsonProcessingException {
        ScanIdentityJobPayload payload = objectMapper.readValue(job.payloadJson(), ScanIdentityJobPayload.class);
        scanService.executeIdentityJob(payload, job.id());
    }

    @Override
    public void afterComplete(BackgroundJobRecord job) throws JsonProcessingException {
        ScanIdentityJobPayload payload = objectMapper.readValue(job.payloadJson(), ScanIdentityJobPayload.class);
        scanService.tryCompleteIdentityScan(payload);
    }
}
