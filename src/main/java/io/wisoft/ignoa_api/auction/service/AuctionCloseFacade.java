package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseFacade {

    private static final long CLOSE_WAIT_MILLIS = 3_000L;

    private final AuctionCloseService auctionCloseService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                CLOSE_WAIT_MILLIS,
                () -> auctionCloseService.closeAuction(itemId)
        );
    }
}

