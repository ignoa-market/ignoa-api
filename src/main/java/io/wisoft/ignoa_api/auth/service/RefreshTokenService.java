package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
                .set(REFRESH_TOKEN_PREFIX + refreshToken, String.valueOf(userId), Duration.ofMillis(jwtProperties.refreshExpiration()));
    }

    public Long getUserId(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);

        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return Long.parseLong(userId);
    }

    public void delete(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }
}
