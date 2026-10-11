package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class LockMetricsTest {

    @Test
    void 생성_시_모든_작업과_결과_조합의_락_지표를_0으로_등록한다() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // When
        new LockMetrics(meterRegistry);

        // Then
        Collection<Timer> acquireWaitTimers = meterRegistry.get("lock.acquire.wait").timers();
        Collection<Timer> holdTimeTimers = meterRegistry.get("lock.hold.time").timers();

        assertThat(acquireWaitTimers)
                .hasSize(LockOperation.values().length * LockAcquireOutcome.values().length)
                .allSatisfy(timer -> assertThat(timer.count()).isZero());
        assertThat(holdTimeTimers)
                .hasSize(LockOperation.values().length)
                .allSatisfy(timer -> assertThat(timer.count()).isZero());

        assertThat(meterRegistry.get("lock.acquire.wait")
                .tags("key", "item:lock", "operation", "auto_close", "outcome", "infra_error")
                .timer().count()).isZero();
    }
}
