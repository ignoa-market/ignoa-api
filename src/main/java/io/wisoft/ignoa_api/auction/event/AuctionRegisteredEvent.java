package io.wisoft.ignoa_api.auction.event;

import java.time.LocalDateTime;

public record AuctionRegisteredEvent(
        Long productId,
        LocalDateTime endTime
) {
}
