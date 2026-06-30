package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AuctionCloseFacade {

    private final AuctionCloseService auctionCloseService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                3,
                TimeUnit.SECONDS,
                () -> auctionCloseService.closeAuction(itemId)
        );
    }
}

