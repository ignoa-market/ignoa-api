package io.wisoft.ignoa_api.global.infra.util;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedisLockUtils {
    private final StringRedisTemplate redisTemplate;

    public <T> T executeWithLock(
            String lockName,
            String value,
            Duration timeout,
            Supplier<T> task
    ) {
        Boolean acquired = acquireLock(
                lockName,
                value,
                timeout
        );

        if (acquired == null || !acquired) {
            return null;
        }

        try {
            return task.get();
        } finally {
            release(lockName);
        }
    }

    private Boolean acquireLock(String lockName, String value, Duration timeout) {
        Boolean getLock = redisTemplate
                .opsForValue()
                .setIfAbsent(
                        generateLockKey(lockName),
                        value,
                        timeout
                );

        return getLock != null && getLock;
    }

    private void release(String lockName) {
        redisTemplate.unlink(generateLockKey(lockName));
    }

    private String generateLockKey(String lockName) {
        return "ignoa-api" + lockName;
    }
}
