package com.pixierge.api.scans;

final class ScanCounts {

    private int scannedFileCount;
    private int addedCount;
    private int unchangedCount;
    private int movedCount;
    private int modifiedCount;
    private int duplicateCount;
    private int missingCount;
    private int reappearedCount;
    private int errorCount;

    void scanned() {
        scannedFileCount++;
    }

    void result(String result) {
        switch (result) {
            case "added" -> addedCount++;
            case "unchanged" -> unchangedCount++;
            case "moved", "renamed" -> movedCount++;
            case "modified" -> modifiedCount++;
            case "duplicate" -> duplicateCount++;
            case "missing" -> missingCount++;
            case "reappeared" -> reappearedCount++;
            case "error" -> errorCount++;
            default -> throw new IllegalArgumentException("Unknown scan result: " + result);
        }
    }

    void add(ScanCounts counts) {
        scannedFileCount += counts.scannedFileCount();
        addedCount += counts.addedCount();
        unchangedCount += counts.unchangedCount();
        movedCount += counts.movedCount();
        modifiedCount += counts.modifiedCount();
        duplicateCount += counts.duplicateCount();
        missingCount += counts.missingCount();
        reappearedCount += counts.reappearedCount();
        errorCount += counts.errorCount();
    }

    int scannedFileCount() {
        return scannedFileCount;
    }

    int addedCount() {
        return addedCount;
    }

    int unchangedCount() {
        return unchangedCount;
    }

    int movedCount() {
        return movedCount;
    }

    int modifiedCount() {
        return modifiedCount;
    }

    int duplicateCount() {
        return duplicateCount;
    }

    int missingCount() {
        return missingCount;
    }

    int reappearedCount() {
        return reappearedCount;
    }

    int errorCount() {
        return errorCount;
    }
}
