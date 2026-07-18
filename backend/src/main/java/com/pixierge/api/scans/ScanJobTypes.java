package com.pixierge.api.scans;

public final class ScanJobTypes {

    /*
     * Job contracts:
     * library-catalog-root dedupes by scan run and serializes by library; retry repeats the same root scan safely.
     * library-catalog-subtree dedupes by scan run and serializes by library; retry reclassifies only the subtree.
     * asset-identity-backfill dedupes by scan run, asset file, size and mtime; stale retries exit without mutation.
     * filesystem-change-event dedupes by root/path while active; retry only enqueues scan work and never mutates assets directly.
     */
    public static final String LIBRARY_CATALOG_ROOT = "library-catalog-root";
    public static final String LIBRARY_CATALOG_SUBTREE = "library-catalog-subtree";
    public static final String ASSET_IDENTITY_BACKFILL = "asset-identity-backfill";
    public static final String FILESYSTEM_CHANGE_EVENT = "filesystem-change-event";

    private ScanJobTypes() {
    }
}
