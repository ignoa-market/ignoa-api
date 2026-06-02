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
public class AuctionTtlService {

    private static final String AUCTION_KEY_PREFIX = "auction:";
    private final StringRedisTemplate stringRedisTemplate;

    public void registerTtl(Long itemId, LocalDateTime endAt) {
        try {
            String key = AUCTION_KEY_PREFIX + itemId;
            long ttl = Duration.between(LocalDateTime.now(), endAt).toSeconds();
            stringRedisTemplate.opsForValue().set(key, "", ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis TTL 등록 실패 itemId={}", itemId, e);
        }
    }

    public void deleteTtl(Long itemId) {
        try {
            String key = AUCTION_KEY_PREFIX + itemId;
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis TTL 삭제 실패 itemId={}", itemId, e);
        }
    }
}
