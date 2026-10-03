package io.wisoft.ignoa_api.trade.payment.dto;

public record PaymentPrepareResponse(
        String orderId,
        Long tradeId,
        Long amount,
        String orderName
) {
}
