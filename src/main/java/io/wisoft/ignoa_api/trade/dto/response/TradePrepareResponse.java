package io.wisoft.ignoa_api.trade.dto.response;

import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareResponse;

public record TradePrepareResponse(
        Long tradeId,
        String orderId,
        Long amount,
        String orderName
) {
    public static TradePrepareResponse from(PaymentPrepareResponse response) {
        return new TradePrepareResponse(
                response.tradeId(),
                response.orderId(),
                response.amount(),
                response.orderName()
        );
    }
}
