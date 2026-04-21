package io.wisoft.ignoa_api.auth.service;

import io.jsonwebtoken.Claims;
import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "bl:";
    private final StringRedisTemplate redisTemplate;

    public void blacklist(String accessToken) {
        Claims claims = (Claims) SecurityContextHolder.getContext()
                .getAuthentication()
                .getCredentials();

        long remainingMillis = claims.getExpiration().getTime() - System.currentTimeMillis();

        redisTemplate.opsForValue()
                .set(ACCESS_TOKEN_BLACKLIST_PREFIX + accessToken,
                        "blacklisted",
                        Duration.ofMillis(remainingMillis));
    }

    public boolean isBlacklisted(String accessToken) {
        return redisTemplate.opsForValue().get(ACCESS_TOKEN_BLACKLIST_PREFIX + accessToken) != null;
    }
}


