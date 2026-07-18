package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.global.infra.lock.LockInfrastructureException;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseFacade {

    private static final long WAIT_TIME_MILLIS = 3_000L;

    private final AuctionCloseService auctionCloseService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        try {
            distributedLock.executeWithLock(
                    ItemLockKey.of(itemId),
                    WAIT_TIME_MILLIS,
                    () -> auctionCloseService.closeAuction(itemId)
            );
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 마감을 진행 itemId={}", itemId, e);
            auctionCloseService.closeAuction(itemId);
        }
    }
}

