package com.pixierge.api.assets;

import org.springframework.core.io.Resource;

import java.time.OffsetDateTime;

record ThumbnailResponseResource(
        Resource resource,
        long contentLength,
        String contentType,
        String etag,
        OffsetDateTime lastModified
) {
}
