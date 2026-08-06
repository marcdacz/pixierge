package com.pixierge.api.assets;

import java.time.OffsetDateTime;
import org.springframework.core.io.Resource;

record ThumbnailResponseResource(
    Resource resource,
    long contentLength,
    String contentType,
    String etag,
    OffsetDateTime lastModified) {}
