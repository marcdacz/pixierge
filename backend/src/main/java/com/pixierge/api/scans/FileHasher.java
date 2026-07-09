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

    static final int BUFFER_SIZE = 64 * 1024;

    Hashes hash(Path path) throws IOException {
        MessageDigest partialDigest = sha256();
        MessageDigest fullDigest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        long partialRemaining = BUFFER_SIZE;
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                if (partialRemaining > 0) {
                    int partialLength = (int) Math.min(read, partialRemaining);
                    partialDigest.update(buffer, 0, partialLength);
                    partialRemaining -= partialLength;
                }
                fullDigest.update(buffer, 0, read);
            }
        }
        return new Hashes(
                HexFormat.of().formatHex(partialDigest.digest()),
                HexFormat.of().formatHex(fullDigest.digest())
        );
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
