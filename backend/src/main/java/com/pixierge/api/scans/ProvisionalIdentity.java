package com.pixierge.api.scans;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

final class ProvisionalIdentity {

    static final String PREFIX = "provisional:";

    private ProvisionalIdentity() {
    }

    static boolean isProvisional(String contentHash) {
        return contentHash != null && contentHash.startsWith(PREFIX);
    }

    static String fingerprint(String normalizedPath, long sizeBytes, OffsetDateTime modifiedAt) {
        String material = normalizedPath + "|" + sizeBytes + "|" + modifiedAt.toInstant().toEpochMilli();
        return PREFIX + digest(material);
    }

    static boolean pathUnchanged(ScanRepository.AssetFileRecord existing, long sizeBytes, OffsetDateTime modifiedAt) {
        return existing.sizeBytes() == sizeBytes && existing.modifiedAt().isEqual(modifiedAt);
    }

    private static String digest(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
