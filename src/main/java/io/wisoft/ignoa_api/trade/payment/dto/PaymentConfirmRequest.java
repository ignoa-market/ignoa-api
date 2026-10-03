package io.wisoft.ignoa_api.trade.payment.dto;

public record PaymentConfirmRequest(
        Long tradeId,
        String orderId,
        String paymentKey,
        Long amount
) {
}
