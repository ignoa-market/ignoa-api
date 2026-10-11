package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class LockMetrics {

    private static final String ITEM_LOCK_KEY = "item:lock";

    private final MeterRegistry meterRegistry;
    private final Map<LockOperation, Map<LockAcquireOutcome, Timer>> acquireWaitTimers = new EnumMap<>(LockOperation.class);
    private final Map<LockOperation, Timer> holdTimeTimers = new EnumMap<>(LockOperation.class);

    public LockMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        for (LockOperation operation : LockOperation.values()) {
            Map<LockAcquireOutcome, Timer> timersByOutcome = new EnumMap<>(LockAcquireOutcome.class);

            for (LockAcquireOutcome outcome : LockAcquireOutcome.values()) {
                timersByOutcome.put(outcome, registerAcquireWaitTimer(operation, outcome));
            }

            acquireWaitTimers.put(operation, timersByOutcome);
            holdTimeTimers.put(operation, registerHoldTimeTimer(operation));
        }
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public void recordAcquireWait(Timer.Sample sample, LockOperation operation, LockAcquireOutcome outcome) {
        sample.stop(acquireWaitTimers.get(operation).get(outcome));
    }

    public <T> T recordHoldTime(LockOperation operation, Supplier<T> task) {
        return holdTimeTimers.get(operation).record(task);
    }

    // 작업 종류 하나의 점유 Timer 등록
    private Timer registerHoldTimeTimer(LockOperation operation) {
        return Timer.builder("lock.hold.time")
                .description("분산 락 점유 시간")
                .tag("key", ITEM_LOCK_KEY)
                .tag("operation", operation.getMetricTag())
                .register(meterRegistry);
    }

    // 작업·결과 조합 하나의 대기 Timer 등록
    private Timer registerAcquireWaitTimer(LockOperation operation, LockAcquireOutcome outcome) {
        return Timer.builder("lock.acquire.wait")
                .description("분산 락 획득 대기 시간")
                .tag("key", ITEM_LOCK_KEY)
                .tag("operation", operation.getMetricTag())
                .tag("outcome", outcome.getMetricTag())
                .register(meterRegistry);
    }
}
