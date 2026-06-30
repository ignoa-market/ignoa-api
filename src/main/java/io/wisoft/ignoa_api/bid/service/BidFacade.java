package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidFacade {

    private final RedissonDistributedLock distributedLock;
    private final BidService bidService;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        return distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                () -> bidService.placeBid(itemId, bidderId, request)
        );
    }
}

