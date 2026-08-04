package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidFacade {

    private static final long BID_WAIT_MILLIS = 150L;

    private final RedissonDistributedLock distributedLock;
    private final BidService bidService;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.BID,
                BID_WAIT_MILLIS,
                () -> bidService.placeBid(itemId, bidderId, request)
        );
    }
}
