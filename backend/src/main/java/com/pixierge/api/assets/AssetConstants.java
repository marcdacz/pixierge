package com.pixierge.api.assets;

final class AssetConstants {

    static final String AVAILABILITY_AVAILABLE = "available";
    static final String AVAILABILITY_MISSING = "missing";
    static final int DEFAULT_PAGE_SIZE = 48;
    static final String DEFAULT_PAGE_SIZE_PARAM = "48";
    static final String EXTRACTION_STATUS_EXTRACTED = "extracted";
    static final String EXTRACTION_STATUS_FAILED = "failed";
    static final String EXTRACTION_STATUS_UNSUPPORTED = "unsupported";
    static final String FILE_STATUS_ACTIVE = "active";
    static final String FILE_STATUS_MISSING = "missing";
    static final String IMAGE_MIME_PREFIX = "image/";
    static final int MAX_PAGE_SIZE = 120;
    static final int METADATA_BACKFILL_BATCH_SIZE = 500;

    private AssetConstants() {
    }
}
