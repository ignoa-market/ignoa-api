package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;


import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockTest {

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock lock;

    RedissonDistributedLock distributedLock;
    SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        distributedLock = new RedissonDistributedLock(redissonClient, meterRegistry);
        given(redissonClient.getLock(anyString())).willReturn(lock);
    }

    @Test
    void 락_획득이_타임아웃되면_LOCK_ACQUISITION_FAILED를_던진다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), any(TimeUnit.class)))
                .willReturn(false);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> "결과")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);
        verify(lock, never()).unlock();
    }

    @Test
    void 락_획득_중_인프라_장애가_발생하면_락_없이_task를_실행한다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), any(TimeUnit.class)))
                .willThrow(new RedisException("Redis 장애"));

        // When
        String result = distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> "결과");

        // Then
        assertThat(result).isEqualTo("결과");
        verify(lock, never()).unlock();
    }

    @Test
    void 락을_정상적으로_획득하면_task를_실행하고_결과를_반환한_후_락을_해제한다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // When
        String result = distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> "입찰 완료");

        // Then
        assertThat(result).isEqualTo("입찰 완료");
        verify(lock).unlock();

        Timer holdTimer = meterRegistry.find("lock.hold.time")
                .tags("key", "item:lock", "operation", "bid")
                .timer();
        assertThat(holdTimer).isNotNull();
        assertThat(holdTimer.count()).isEqualTo(1);

        Timer acquireWaitTimer = meterRegistry.find("lock.acquire.wait")
                .tags(
                        "key", "item:lock",
                        "operation", "bid",
                        "outcome", "acquired"
                )
                .timer();
        assertThat(acquireWaitTimer).isNotNull();
        assertThat(acquireWaitTimer.count()).isEqualTo(1);
    }

    @Test
    void 락_보유_여부_확인이_실패해도_task_결과를_반환한다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willThrow(new RedisException("Redis 장애"));

        // When
        String result = distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> "입찰 완료");

        // Then
        assertThat(result).isEqualTo("입찰 완료");
        verify(lock, never()).unlock();
    }

    @Test
    void 락_해제가_실패해도_task_결과를_반환한다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        willThrow(new RedisException("Redis 장애")).given(lock).unlock();

        // When
        String result = distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> "입찰 완료");

        // Then
        assertThat(result).isEqualTo("입찰 완료");
        verify(lock).unlock();
    }

    @Test
    void 락_해제가_실패해도_task를_재실행하지_않는다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;
        AtomicInteger executionCount = new AtomicInteger();

        given(lock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        willThrow(new RedisException("Redis 장애")).given(lock).unlock();

        // When
        distributedLock.executeWithLockOrFailOpen(key, LockOperation.BID, waitTime, () -> {
            executionCount.incrementAndGet();
            return "입찰 완료";
        });

        // Then
        assertThat(executionCount.get()).isEqualTo(1);
    }
}
