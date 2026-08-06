package com.pixierge.api.catalog;

import java.util.UUID;

/** Stable asset identity used by recovery after a source-library rescan. */
public record CatalogAssetReference(UUID sourceLibraryId, String confirmedContentHash) {}
