package io.wisoft.ignoa_api.bid.dto.response;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.entity.BidStatus;

import java.time.LocalDateTime;

public record BidSummary(
        Long bidId,
        String bidderNickname,
        Long price,
        BidStatus status,
        LocalDateTime createdAt
) {
    public static BidSummary from(Bid bid) {
        return new BidSummary(
                bid.getId(),
                bid.getBidder().getNickname(),
                bid.getPrice(),
                bid.getStatus(),
                bid.getCreatedAt()
        );
    }
}
