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

    private final AuctionService auctionService;
    private final RedissonDistributedLock distributedLock;

    public void closeAuction(Long itemId) {
        distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.AUTO_CLOSE,
                () -> auctionService.closeAuction(itemId)
        );
    }

    public AuctionExtensionResponse extendAuction(Long itemId, Long userId) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.EXTEND,
                () -> auctionService.extendAuction(itemId, userId)
        );
    }
}
