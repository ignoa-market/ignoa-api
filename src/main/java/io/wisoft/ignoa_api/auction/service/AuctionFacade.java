package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionFacade {

    private static final long CLOSE_WAIT_MILLIS = 500L;
    private static final long EXTEND_WAIT_MILLIS = 250L;

    private final AuctionService auctionService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.AUTO_CLOSE,
                CLOSE_WAIT_MILLIS,
                () -> auctionService.closeAuction(itemId)
        );
    }

    public AuctionExtensionResponse extendAuction(Long itemId, Long userId) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.EXTEND,
                EXTEND_WAIT_MILLIS,
                () -> auctionService.extendAuction(itemId, userId)
        );
    }
}
