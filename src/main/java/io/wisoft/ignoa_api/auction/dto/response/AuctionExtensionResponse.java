package io.wisoft.ignoa_api.auction.dto.response;

import java.time.LocalDateTime;

public record AuctionExtensionResponse(
        Long itemId,
        LocalDateTime endAt,
        int extensionCount
) {
}
