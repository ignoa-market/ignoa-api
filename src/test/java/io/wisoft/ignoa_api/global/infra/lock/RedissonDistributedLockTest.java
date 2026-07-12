package io.wisoft.ignoa_api.global.infra.lock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockTest {

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock lock;

    RedissonDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new RedissonDistributedLock(redissonClient, new SimpleMeterRegistry());
        given(redissonClient.getLock(anyString())).willReturn(lock);
    }

    @Test
    void 락_획득이_타임아웃되면_LOCK_ACQUISITION_FAILED를_던진다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .willReturn(false);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> distributedLock.executeWithLock(key, waitTime, () -> "결과")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);
        verify(lock, never()).unlock();
    }

    @Test
    void 락_획득_중_인프라_장애가_발생하면_LockInfrastructureException을_던진다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .willThrow(new RedisException("Redis 장애"));

        // When
        LockInfrastructureException exception = catchThrowableOfType(
                LockInfrastructureException.class,
                () -> distributedLock.executeWithLock(key, waitTime, () -> "결과")
        );

        // Then
        assertThat(exception)
                .isNotNull()
                .hasCauseInstanceOf(RedisException.class);
        verify(lock, never()).unlock();
    }

    @Test
    void 락을_정상적으로_획득하면_task를_실행하고_결과를_반환한_후_락을_해제한다() throws InterruptedException {
        // Given
        String key = "item:lock:1";
        long waitTime = 250L;

        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // When
        String result = distributedLock.executeWithLock(key, waitTime, () -> "입찰 완료");

        // Then
        assertThat(result).isEqualTo("입찰 완료");
        verify(lock).unlock();
    }
}