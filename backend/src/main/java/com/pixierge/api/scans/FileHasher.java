package com.pixierge.api.scans;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
class FileHasher {

    private static final int BUFFER_SIZE = 64 * 1024;

    Hashes hash(Path path) throws IOException {
        return new Hashes(hash(path, BUFFER_SIZE), hash(path, Long.MAX_VALUE));
    }

    private String hash(Path path, long maxBytes) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = maxBytes;
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while (remaining > 0 && (read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    record Hashes(String partialHash, String contentHash) {
    }
}
