package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import io.wisoft.ignoa_api.global.infra.redis.RedisOperationExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "rt:";
    private static final String USER_TOKEN_PREFIX = "rt:user:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final RedisOperationExecutor redisOperationExecutor;

    public void save(String refreshToken, Long userId) {
        Duration ttl = Duration.ofMillis(jwtProperties.refreshExpiration());
        String userKey = USER_TOKEN_PREFIX + userId;

        // 목록을 먼저 저장해, 중간에 실패해도 전체 폐기 대상에서 빠지는 토큰이 없게 함
        redisOperationExecutor.run(() -> {
            redisTemplate.opsForSet().add(userKey, refreshToken);   // SADD rt:user:{userId} {토큰}
            redisTemplate.expire(userKey, ttl);                     // EXPIRE rt:user:{userId} {유효기간}

            // SET rt:{토큰} {userId} PX {유효기간}
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, String.valueOf(userId), ttl);
        });
    }

    public void deleteAllByUserId(Long userId) {
        String userKey = USER_TOKEN_PREFIX + userId;

        redisOperationExecutor.run(() -> {
            Set<String> tokens = redisTemplate.opsForSet().members(userKey);    // SMEMBERS rt:user:{userId}

            if (tokens == null || tokens.isEmpty()) {
                return;
            }

            List<String> keys = new ArrayList<>(tokens.size() + 1);
            tokens.forEach(token -> keys.add(REFRESH_TOKEN_PREFIX + token));
            keys.add(userKey);

            redisTemplate.delete(keys);
        });
    }

    public Long consume(String refreshToken) {
        String userId = redisOperationExecutor.execute(() -> {
            String storedUserId = redisTemplate.opsForValue()
                    .getAndDelete(REFRESH_TOKEN_PREFIX + refreshToken);    // GETDEL rt:{토큰}

            if (storedUserId != null) {
                redisTemplate.opsForSet().remove(USER_TOKEN_PREFIX + storedUserId, refreshToken);  // SREM rt:user:{userId} {토큰}
            }

            return storedUserId;
        });

        return userId != null ? Long.parseLong(userId) : null;
    }

    public void delete(String refreshToken) {
        redisOperationExecutor.run(() -> {
            String userId = redisTemplate.opsForValue()
                    .getAndDelete(REFRESH_TOKEN_PREFIX + refreshToken);    // GETDEL rt:{토큰}

            if (userId != null) {
                redisTemplate.opsForSet().remove(USER_TOKEN_PREFIX + userId, refreshToken);  // SREM rt:user:{userId} {토큰}
            }
        });
    }
}


