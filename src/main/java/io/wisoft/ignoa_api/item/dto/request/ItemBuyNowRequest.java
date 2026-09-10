package io.wisoft.ignoa_api.item.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemBuyNowRequest(
        @NotNull(message = "즉시 구매가는 필수입니다")
        @Min(value = 0, message = "즉시 구매가는 0원 이상이어야 합니다")
        Long buyNowPrice
) {
}
