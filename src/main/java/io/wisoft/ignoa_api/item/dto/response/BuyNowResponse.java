package io.wisoft.ignoa_api.item.dto.response;

import java.time.LocalDateTime;

public record BuyNowResponse(
        Long tradeId,
        Long itemId,
        Long price,
        LocalDateTime paymentDeadline
) {
}
