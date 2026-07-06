package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class AuctionCloseFacade {

    private static final long WAIT_TIME_MILLIS = 3_000L;

    private final AuctionCloseService auctionCloseService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                WAIT_TIME_MILLIS,
                () -> auctionCloseService.closeAuction(itemId)
        );
    }
}

