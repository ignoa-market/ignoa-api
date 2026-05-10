package io.wisoft.ignoa_api.global.infra.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisLockUtils {
    private final StringRedisTemplate redisTemplate;

    public Boolean acquireLock(String lockName, String value, Duration timeout) {
        Boolean getLock = redisTemplate
                .opsForValue()
                .setIfAbsent(
                        generateLockKey(lockName),
                        value,
                        timeout
                );

        return getLock != null && getLock;
    }

    private String generateLockKey(String lockName) {
        return "ignoa-api" + lockName;
    }
}
