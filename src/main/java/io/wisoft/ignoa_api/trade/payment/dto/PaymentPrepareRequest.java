package io.wisoft.ignoa_api.trade.payment.dto;

public record PaymentPrepareRequest(
        Long tradeId,
        Long amount,
        String orderName
) {
}
