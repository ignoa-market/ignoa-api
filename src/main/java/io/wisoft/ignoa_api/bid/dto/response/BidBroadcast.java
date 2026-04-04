package io.wisoft.ignoa_api.bid.dto.response;

import java.time.LocalDateTime;

public record BidBroadcast(
        Long itemId,
        Long currentPrice,
        String bidderNickname,
        LocalDateTime createdAt
) {
}
