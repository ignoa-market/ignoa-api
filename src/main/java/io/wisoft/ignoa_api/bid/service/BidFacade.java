package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.global.infra.lock.LockInfrastructureException;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidFacade {

    private static final long WAIT_TIME_MILLIS = 250L;

    private final RedissonDistributedLock distributedLock;
    private final BidService bidService;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        try {
            return distributedLock.executeWithLock(
                    ItemLockKey.of(itemId),
                    WAIT_TIME_MILLIS,
                    () -> bidService.placeBid(itemId, bidderId, request)
            );
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 입찰을 진행 itemId={}, bidderId={}", itemId, bidderId, e);
            return bidService.placeBid(itemId, bidderId, request);
        }
    }
}

