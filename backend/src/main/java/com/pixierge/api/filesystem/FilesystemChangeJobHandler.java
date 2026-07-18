package com.pixierge.api.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobHandler;
import com.pixierge.api.background.BackgroundJobRecord;
import com.pixierge.api.scans.ScanJobTypes;
import com.pixierge.api.scans.ScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class FilesystemChangeJobHandler implements BackgroundJobHandler {

    private final FilesystemChangeScanEnqueuer scanEnqueuer;
    private final ObjectMapper objectMapper;

    @Autowired
    FilesystemChangeJobHandler(ScanService scanService, ObjectMapper objectMapper) {
        this(scanService::enqueueFilesystemChangeScan, objectMapper);
    }

    FilesystemChangeJobHandler(FilesystemChangeScanEnqueuer scanEnqueuer, ObjectMapper objectMapper) {
        this.scanEnqueuer = scanEnqueuer;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return ScanJobTypes.FILESYSTEM_CHANGE_EVENT;
    }

    @Override
    public void handle(BackgroundJobRecord job) throws JsonProcessingException {
        FilesystemChangeJobPayload payload = objectMapper.readValue(
                job.payloadJson(),
                FilesystemChangeJobPayload.class
        );
        scanEnqueuer.enqueueFilesystemChangeScan(payload.libraryId(), payload.rootId(), payload.path());
    }

    @FunctionalInterface
    interface FilesystemChangeScanEnqueuer {
        void enqueueFilesystemChangeScan(UUID libraryId, UUID rootId, String path);
    }
}
