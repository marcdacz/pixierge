package com.pixierge.api.assets;

import static com.pixierge.api.assets.AssetConstants.IDENTITY_STATUS_CONFIRMED;
import static com.pixierge.api.assets.AssetConstants.IDENTITY_STATUS_PENDING;

final class AssetIdentity {

    private static final String PROVISIONAL_PREFIX = "provisional:";

    private AssetIdentity() {
    }

    static String statusFor(String contentHash) {
        return contentHash != null && contentHash.startsWith(PROVISIONAL_PREFIX)
                ? IDENTITY_STATUS_PENDING
                : IDENTITY_STATUS_CONFIRMED;
    }
}
