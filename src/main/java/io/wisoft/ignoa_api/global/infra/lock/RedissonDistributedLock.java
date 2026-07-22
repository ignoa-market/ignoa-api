package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonDistributedLock {

    private static final long LEASE_TIME_MILLIS = 10_000L;

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public <T> T executeWithLockOrFailOpen(String key, LockOperation operation, long waitMillis, Supplier<T> task) {
        try {
            return execute(key, operation, waitMillis, task);
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 진행. key={}, operation={}", key, operation, e);
            return task.get();
        }
    }

    public void executeWithLockOrFailOpen(String key, LockOperation operation, long waitMillis, Runnable task) {
        try {
            execute(key, operation, waitMillis, toSupplier(task));
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 진행. key={}, operation={}", key, operation, e);
            task.run();
        }
    }

    // 실제 락 획득 로직, Task를 실행하고 락을 해제한다.
    private <T> T execute(String key, LockOperation operation, long waitMillis, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);

        boolean acquired = acquireLock(lock, key, operation, waitMillis);

        if (!acquired) {
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        }

        try {
            return recordHoldTime(key, operation, task);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean acquireLock(RLock lock, String key, LockOperation operation, long waitMillis) {
        Sample sample = Timer.start(meterRegistry);
        LockAcquireOutcome outcome = LockAcquireOutcome.ERROR;

        try {
            boolean acquired = lock.tryLock(
                    waitMillis,
                    LEASE_TIME_MILLIS,
                    TimeUnit.MILLISECONDS
            );

            outcome = acquired
                    ? LockAcquireOutcome.ACQUIRED
                    : LockAcquireOutcome.TIMEOUT;

            return acquired;

        } catch (InterruptedException e) {
            outcome = LockAcquireOutcome.INTERRUPTED;
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);

        } catch (RedisException e) {
            outcome = LockAcquireOutcome.INFRA_ERROR;
            throw new LockInfrastructureException("Redis 인프라 장애 - 분산 락 획득 실패, key = " + key, e);

        } finally {
            recordAcquireWaitTime(sample, key, operation, outcome);
        }
    }

    private void recordAcquireWaitTime(Sample sample, String key, LockOperation operation, LockAcquireOutcome outcome) {
        Timer acquireWaitTimer = Timer.builder("lock.acquire.wait")
                .tags("key", keyPrefix(key))
                .tag("operation", operation.metricTag())
                .tags("outcome", outcome.metricTag())
                .register(meterRegistry);

        sample.stop(acquireWaitTimer);
    }

    private <T> T recordHoldTime(String key, LockOperation operation, Supplier<T> task) {
        Timer holdTimer = Timer.builder("lock.hold.time")
                .tag("key", keyPrefix(key))
                .tag("operation", operation.metricTag())
                .register(meterRegistry);

        return holdTimer.record(task);
    }

    private static Supplier<Object> toSupplier(Runnable task) {
        return () -> {
            task.run();
            return null;
        };
    }

    private String keyPrefix(String key) {
        int i = key.lastIndexOf(':');
        return i > 0 ? key.substring(0, i) : key;
    }
}
