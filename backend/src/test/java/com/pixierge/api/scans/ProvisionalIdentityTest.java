package com.pixierge.api.scans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProvisionalIdentityTest {

  @Test
  void identifiesProvisionalHashesAndCreatesStableFingerprints() {
    OffsetDateTime modifiedAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");

    String first = ProvisionalIdentity.fingerprint("/photos/one.jpg", 42, modifiedAt);
    String second = ProvisionalIdentity.fingerprint("/photos/one.jpg", 42, modifiedAt);

    assertThat(first).isEqualTo(second);
    assertThat(first).startsWith(ProvisionalIdentity.PREFIX);
    assertThat(ProvisionalIdentity.isProvisional(first)).isTrue();
    assertThat(ProvisionalIdentity.isProvisional("sha256")).isFalse();
    assertThat(ProvisionalIdentity.isProvisional(null)).isFalse();
  }

  @Test
  void pathUnchangedComparesSizeAndModifiedAt() {
    OffsetDateTime modifiedAt = OffsetDateTime.parse("2026-07-30T00:00:00Z");
    ScanRepository.AssetFileRecord file =
        new ScanRepository.AssetFileRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "/photos/one.jpg",
            "/photos/one.jpg",
            "one.jpg",
            42,
            modifiedAt,
            "sha256",
            "active");

    assertThat(ProvisionalIdentity.pathUnchanged(file, 42, modifiedAt)).isTrue();
    assertThat(ProvisionalIdentity.pathUnchanged(file, 43, modifiedAt)).isFalse();
    assertThat(ProvisionalIdentity.pathUnchanged(file, 42, modifiedAt.plusSeconds(1))).isFalse();
  }
}
