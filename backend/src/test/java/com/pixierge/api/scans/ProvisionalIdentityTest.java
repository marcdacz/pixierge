package com.pixierge.api.scans;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionalIdentityTest {

    @Test
    void detectsProvisionalHashes() {
        String fingerprint = ProvisionalIdentity.fingerprint("/photos/beach.jpg", 42, OffsetDateTime.parse("2026-07-04T00:00:00Z"));

        assertThat(ProvisionalIdentity.isProvisional(fingerprint)).isTrue();
        assertThat(ProvisionalIdentity.isProvisional("deadbeef")).isFalse();
    }

    @Test
    void unchangedPathComparesSizeAndModifiedTime() {
        ScanRepository.AssetFileRecord existing = new ScanRepository.AssetFileRecord(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "/photos/beach.jpg",
                "/photos/beach.jpg",
                "beach.jpg",
                42,
                OffsetDateTime.parse("2026-07-04T00:00:00Z"),
                "confirmed-hash",
                "active"
        );

        assertThat(ProvisionalIdentity.pathUnchanged(existing, 42, OffsetDateTime.parse("2026-07-04T00:00:00Z"))).isTrue();
        assertThat(ProvisionalIdentity.pathUnchanged(existing, 43, OffsetDateTime.parse("2026-07-04T00:00:00Z"))).isFalse();
    }
}
