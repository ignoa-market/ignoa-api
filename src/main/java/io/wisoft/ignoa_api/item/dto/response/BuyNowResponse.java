package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;

public record BuyNowResponse(
        Long itemId,
        Long buyerId,
        Long price,
        ItemStatus status
) {
}
