package io.wisoft.ignoa_api.trade.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TradeConfirmRequest(
        @NotBlank(message = "주문 번호는 필수입니다.")
        String orderId,

        @NotBlank(message = "결제 키는 필수입니다.")
        String paymentKey,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
        Long amount
) {
}

