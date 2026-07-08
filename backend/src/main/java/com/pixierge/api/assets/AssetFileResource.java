package com.pixierge.api.assets;

import org.springframework.core.io.Resource;

public record AssetFileResource(
        Resource resource,
        String contentType,
        long contentLength
) {
}
