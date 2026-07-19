package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    public <T> T executeWithLockOrFailOpen(String key, long waitMillis, Supplier<T> task) {
        try {
            return execute(key, waitMillis, task);
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 진행. key={}", key, e);
            return task.get();
        }
    }

    public void executeWithLockOrFailOpen(String key, long waitMillis, Runnable task) {
        try {
            execute(key, waitMillis, toSupplier(task));
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 진행. key={}", key, e);
            task.run();
        }
    }

    private <T> T execute(String key, long waitMillis, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;

        try {
            acquired = lock.tryLock(waitMillis, LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } catch (RedisException e) {
            throw new LockInfrastructureException("Redis 인프라 장애 - 분산 락 획득 실패, key = " + key, e);
        }

        if (!acquired) {
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        }

        try {
            Timer holdTimer = Timer.builder("lock.hold.time")
                    .tag("key", keyPrefix(key))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry);

            return holdTimer.record(task);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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
