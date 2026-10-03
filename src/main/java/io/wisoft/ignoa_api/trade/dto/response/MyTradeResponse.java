package io.wisoft.ignoa_api.trade.dto.response;

import io.wisoft.ignoa_api.trade.entity.Trade;

import java.time.LocalDateTime;

public record MyTradeResponse(
        Long tradeId,
        Long itemId,
        String type,
        String status,
        Long amount,
        LocalDateTime paymentDeadline,
        LocalDateTime paidAt
) {
    public static MyTradeResponse from(Trade trade) {
        return new MyTradeResponse(
                trade.getId(),
                trade.getItem().getId(),
                trade.getType().name(),
                trade.getStatus().name(),
                trade.getAmount(),
                trade.getPaymentDeadline(),
                trade.getPaidAt()
        );
    }
}
