package io.wisoft.ignoa_api.trade.payment.dto;

public record PaymentServerResponse<T>(
        T data,
        String message
) {
}
