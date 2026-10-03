package io.wisoft.ignoa_api.trade.dto.response;

import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;

public record TradeConfirmResponse(
        Long tradeId,
        String orderId,
        String status,
        String failureCode,
        String failureMessage
) {
    public static TradeConfirmResponse from(PaymentResult result) {
        return new TradeConfirmResponse(
                result.tradeId(),
                result.orderId(),
                result.status(),
                result.failureCode(),
                result.failureMessage()
        );
    }
}
