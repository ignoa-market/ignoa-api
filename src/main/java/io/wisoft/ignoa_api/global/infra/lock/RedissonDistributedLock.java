package io.wisoft.ignoa_api.global.infra.lock;

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

    private final RedissonClient redissonClient;
    private final LockMetrics lockMetrics;

    public <T> T executeWithLock(String key, LockOperation operation, Supplier<T> task) {
        try {
            return execute(key, operation, task);

        } catch (LockInfrastructureException e) {
            log.debug("분산 락 Fail-Closed: key={}, operation={}, reason=Redis 인프라 장애", key, operation);
            throw new BusinessException(ErrorCode.LOCK_INFRASTRUCTURE_ERROR, e);
        }
    }

    // 락 획득 → 작업 실행 → 락 해제
    private <T> T execute(String key, LockOperation operation, Supplier<T> task) {
        RLock lock = redissonClient.getLock(key);
        acquireLock(lock, key, operation);

        try {
            return lockMetrics.recordHoldTime(operation, task);
        } finally {
            release(lock, key, operation);
        }
    }

    private void acquireLock(RLock lock, String key, LockOperation operation) {
        Sample sample = lockMetrics.start();
        LockAcquireOutcome outcome = LockAcquireOutcome.ERROR;

        try {
            boolean acquired = lock.tryLock(operation.getWaitMillis(), TimeUnit.MILLISECONDS);

            if (!acquired) {
                outcome = LockAcquireOutcome.TIMEOUT;
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            outcome = LockAcquireOutcome.ACQUIRED;

        } catch (InterruptedException e) {
            outcome = LockAcquireOutcome.INTERRUPTED;
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);

        } catch (RedisException e) {
            outcome = LockAcquireOutcome.INFRA_ERROR;
            throw new LockInfrastructureException("Redis 인프라 장애 - 분산 락 획득 실패, key = " + key, e);

        } finally {
            lockMetrics.recordAcquireWait(sample, operation, outcome);
        }
    }

    private static void release(RLock lock, String key, LockOperation operation) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }

        } catch (RedisException e) {
            log.warn("분산 락 해제 실패 - 작업은 정상 완료됨. key={}, operation={}", key, operation, e);
        }
    }
}
