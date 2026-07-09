package com.pixierge.api.scans;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScanProgressThrottleTest {

    @Test
    void publishesAfterFileInterval() {
        ScanCounts counts = new ScanCounts();
        FakeProgressRepository repository = new FakeProgressRepository();
        ScanProgressThrottle throttle = new ScanProgressThrottle(UUID.randomUUID(), repository, counts);

        for (int index = 0; index < ScanProgressThrottle.FILE_INTERVAL - 1; index++) {
            counts.scanned();
            throttle.maybeUpdate();
        }

        assertThat(repository.updates).isZero();
        counts.scanned();
        assertThat(throttle.shouldUpdate()).isTrue();
        throttle.flush();
        assertThat(repository.updates).isEqualTo(1);
    }

    @Test
    void shouldUpdateWhenTimeIntervalElapsed() {
        ScanCounts counts = new ScanCounts();
        ScanProgressThrottle throttle = new ScanProgressThrottle(UUID.randomUUID(), new FakeProgressRepository(), counts);

        counts.scanned();
        throttle.flush();

        assertThat(throttle.shouldUpdate()).isFalse();
    }

    private static class FakeProgressRepository extends ScanRepository {

        private int updates;

        FakeProgressRepository() {
            super(null);
        }

        @Override
        void updateScanRunProgress(UUID scanRunId, ScanCounts counts) {
            updates++;
        }
    }
}
