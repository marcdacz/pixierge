package com.pixierge.api.assets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobHandler;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.scans.ScanJobTypes;
import org.springframework.stereotype.Component;

@Component
class AssetMetadataJobHandler implements BackgroundJobHandler {

    private final MetadataEnrichmentService metadataEnrichmentService;
    private final ObjectMapper objectMapper;

    AssetMetadataJobHandler(MetadataEnrichmentService metadataEnrichmentService, ObjectMapper objectMapper) {
        this.metadataEnrichmentService = metadataEnrichmentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return ScanJobTypes.ASSET_METADATA_BACKFILL;
    }

    @Override
    public void handle(BackgroundJobRecord job) throws JsonProcessingException {
        AssetMetadataJobPayload payload = objectMapper.readValue(job.payloadJson(), AssetMetadataJobPayload.class);
        metadataEnrichmentService.extractQueuedMetadata(payload, job.id());
    }
}
