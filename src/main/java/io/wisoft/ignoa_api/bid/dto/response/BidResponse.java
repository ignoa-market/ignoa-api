package io.wisoft.ignoa_api.bid.dto.response;

import io.wisoft.ignoa_api.bid.entity.Bid;

import java.time.LocalDateTime;

public record BidResponse(
        Long bidId,
        Long itemId,
        Long bidderId,
        Long price,
        LocalDateTime createdAt
) {
    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getItem().getId(),
                bid.getBidder().getId(),
                bid.getPrice(),
                bid.getCreatedAt()
        );
    }
}
