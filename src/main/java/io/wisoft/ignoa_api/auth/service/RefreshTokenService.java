package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
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

    public void save(String refreshToken, Long userId) {
        redisTemplate.opsForValue()
                .set(REFRESH_TOKEN_PREFIX + refreshToken,
                        String.valueOf(userId),
                        Duration.ofMillis(jwtProperties.refreshExpiration())
                );
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    public void deleteAllByUserId(Long userId) {
        String pattern = REFRESH_TOKEN_PREFIX + "*";
        String targetUserId = String.valueOf(userId);

        redisTemplate.scan(ScanOptions.scanOptions().match(pattern).build())
                .forEachRemaining(key -> {
                    if(targetUserId.equals(redisTemplate.opsForValue().get(key))) {
                        redisTemplate.delete(key);
                    }
                });
    }

    public Long consumeToken(String refreshToken) {
        String userId = redisTemplate.opsForValue()
                .getAndDelete(REFRESH_TOKEN_PREFIX + refreshToken);

        return userId != null ? Long.parseLong(userId) : null;
    }
}


