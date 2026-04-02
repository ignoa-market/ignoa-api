package io.wisoft.ignoa_api.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionRedisService {

    private static final String AUCTION_KEY_PREFIX = "auction:";
    private final StringRedisTemplate stringRedisTemplate;

    public void registerTtl(Long productId, LocalDateTime endTime) {
        try {
            String key = AUCTION_KEY_PREFIX + productId;
            long ttl = Duration.between(LocalDateTime.now(), endTime).toSeconds();
            stringRedisTemplate.opsForValue().set(key, "", ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis TTL 등록 실패 productId={}", productId, e);
        }
    }

    public void deleteTtl(Long productId) {
        try {
            String key = AUCTION_KEY_PREFIX + productId;
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis TTL 삭제 실패 productId={}", productId, e);
        }
    }
}
