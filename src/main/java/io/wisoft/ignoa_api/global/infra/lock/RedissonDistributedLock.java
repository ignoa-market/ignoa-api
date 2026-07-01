package io.wisoft.ignoa_api.global.infra.lock;

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

    private static final long DEFAULT_LEASE_TIME = 10L;
    private final RedissonClient redissonClient;

    public <T> T executeWithLock(String key, Supplier<T> task) {
        return executeWithLock(key, 0, DEFAULT_LEASE_TIME, TimeUnit.SECONDS, task);
    }

    public void executeWithLock(String key, long waitTime, TimeUnit unit, Runnable task) {
        executeWithLock(key, waitTime, DEFAULT_LEASE_TIME, unit, () -> {
            task.run();
            return null;
        });
    }

    private <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(waitTime, leaseTime, unit);

            if (!acquired) {
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
