package com.pixierge.api.scans;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class FileHasherTest {

    @TempDir
    private Path tempDir;

    @Test
    void computesPartialAndFullHashesInOneFileRead() throws IOException {
        Path file = tempDir.resolve("photo.jpg");
        byte[] content = new byte[FileHasher.BUFFER_SIZE + 128];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index % 251);
        }
        Files.write(file, content);

        FileHasher hasher = new FileHasher();
        FileHasher.Hashes hashes = hasher.hash(file);

        assertThat(hashes.partialHash()).isEqualTo(digest(content, FileHasher.BUFFER_SIZE));
        assertThat(hashes.contentHash()).isEqualTo(digest(content, content.length));
        assertThat(hashes.partialHash()).isNotEqualTo(hashes.contentHash());
    }

    private static String digest(byte[] content, long maxBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        int length = (int) Math.min(content.length, maxBytes);
        digest.update(content, 0, length);
        return HexFormat.of().formatHex(digest.digest());
    }
}
