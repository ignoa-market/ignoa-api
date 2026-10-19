package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.metric.BidMetrics;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidFacade {

    private final BidService bidService;
    private final BidMetrics bidMetrics;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        try {
            BidResponse response = bidService.placeBid(itemId, bidderId, request);
            bidMetrics.countAccepted();
            return response;

        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.BID_CONFLICT) {
                bidMetrics.countPriceConflict();
            }
            throw e;
        }
    }
}
