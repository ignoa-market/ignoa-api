package io.wisoft.ignoa_api.trade.payment.dto;

import java.time.LocalDateTime;

public record PaymentResult(
        Long tradeId,
        String orderId,
        String status,
        Long amount,
        LocalDateTime approvedAt,
        String failureCode,
        String failureMessage
) {
    private static final String UNKNOWN = "UNKNOWN";

    public static PaymentResult unknown(PaymentConfirmRequest request) {
        return new PaymentResult(
                request.tradeId(),
                request.orderId(),
                UNKNOWN,
                request.amount(),
                null,
                null,
                null
        );
    }
}
