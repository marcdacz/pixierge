package com.pixierge.api.scans;

public final class ScanJobTypes {

    public static final String LIBRARY_CATALOG_ROOT = "library-catalog-root";
    public static final String LIBRARY_CATALOG_SUBTREE = "library-catalog-subtree";
    public static final String ASSET_IDENTITY_BACKFILL = "asset-identity-backfill";
    public static final String FILESYSTEM_CHANGE_EVENT = "filesystem-change-event";

    private ScanJobTypes() {
    }
}
