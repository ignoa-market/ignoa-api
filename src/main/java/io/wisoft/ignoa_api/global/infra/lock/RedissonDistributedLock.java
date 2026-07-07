package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedissonDistributedLock {

    private static final long LEASE_TIME_MILLIS = 10_000L;

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public <T> T executeWithLock(String key, long waitMillis, Supplier<T> task) {
        return execute(key, waitMillis, task);
    }

    public void executeWithLock(String key, long waitMillis, Runnable task) {
        execute(key, waitMillis, toSupplier(task));
    }

    private <T> T execute(String key, long waitMillis, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitMillis, LEASE_TIME_MILLIS, TimeUnit.MILLISECONDS);

            if (!acquired) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            Timer holdTimer = Timer.builder("lock.hold.time")
                    .tag("key", keyPrefix(key))
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry);

            return holdTimer.record(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
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
