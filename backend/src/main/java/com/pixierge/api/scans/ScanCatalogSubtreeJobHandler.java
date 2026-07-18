package com.pixierge.api.scans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobHandler;
import com.pixierge.api.background.BackgroundJobRecord;
import org.springframework.stereotype.Component;

@Component
class ScanCatalogSubtreeJobHandler implements BackgroundJobHandler {

    private final ScanService scanService;
    private final ObjectMapper objectMapper;

    ScanCatalogSubtreeJobHandler(ScanService scanService, ObjectMapper objectMapper) {
        this.scanService = scanService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return ScanJobTypes.LIBRARY_CATALOG_SUBTREE;
    }

    @Override
    public void handle(BackgroundJobRecord job) throws JsonProcessingException {
        ScanCatalogJobPayload payload = objectMapper.readValue(job.payloadJson(), ScanCatalogJobPayload.class);
        scanService.executeCatalogJob(payload, job.id());
    }

    @Override
    public void afterComplete(BackgroundJobRecord job) throws JsonProcessingException {
        ScanCatalogJobPayload payload = objectMapper.readValue(job.payloadJson(), ScanCatalogJobPayload.class);
        scanService.tryCompleteCatalogScan(payload);
    }
}
