package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionFacade {

    private static final long CLOSE_WAIT_MILLIS = 3_000L;
    private static final long EXTEND_WAIT_MILLIS = 250L;

    private final AuctionService auctionService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                CLOSE_WAIT_MILLIS,
                () -> auctionService.closeAuction(itemId)
        );
    }

    public AuctionExtensionResponse extendAuction(Long itemId, Long userId) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                EXTEND_WAIT_MILLIS,
                () -> auctionService.extendAuction(itemId, userId)
        );
    }
}

