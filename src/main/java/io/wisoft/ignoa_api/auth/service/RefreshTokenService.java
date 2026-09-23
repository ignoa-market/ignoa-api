package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import io.wisoft.ignoa_api.global.infra.redis.RedisOperationExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "rt:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final RedisOperationExecutor redisOperationExecutor;

    public void save(String refreshToken, Long userId) {
        redisOperationExecutor.run(() ->
                redisTemplate.opsForValue()
                        .set(REFRESH_TOKEN_PREFIX + refreshToken,
                                String.valueOf(userId),
                                Duration.ofMillis(jwtProperties.refreshExpiration())
                        )
        );
    }

    public void delete(String refreshToken) {
        redisOperationExecutor.run(() ->
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken)
        );
    }

    public void deleteAllByUserId(Long userId) {
        String pattern = REFRESH_TOKEN_PREFIX + "*";
        String targetUserId = String.valueOf(userId);

        redisOperationExecutor.run(() -> {
            try (Cursor<String> cursor =
                         redisTemplate.scan(
                                 ScanOptions.scanOptions().match(pattern).build()
                         )) {

                cursor.forEachRemaining(key -> {
                    if (targetUserId.equals(redisTemplate.opsForValue().get(key))) {
                        redisTemplate.delete(key);
                    }
                });
            }
        });
    }

    public Long consume(String refreshToken) {
        String userId = redisOperationExecutor.execute(() ->
                redisTemplate.opsForValue()
                        .getAndDelete(REFRESH_TOKEN_PREFIX + refreshToken)
        );

        return userId != null ? Long.parseLong(userId) : null;
    }
}


