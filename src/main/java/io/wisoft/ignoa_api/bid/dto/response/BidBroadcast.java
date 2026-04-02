package io.wisoft.ignoa_api.bid.dto.response;

import java.time.LocalDateTime;

public record BidBroadcast(
        Long productId,
        Long currentPrice,
        String bidderName,
        LocalDateTime createdAt
) {
}
