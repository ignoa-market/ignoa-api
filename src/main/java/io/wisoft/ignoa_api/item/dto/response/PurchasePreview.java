package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;

public record PurchasePreview(
        Long tradeId,
        TradeType tradeType,
        TradeStatus tradeStatus,
        ItemPreview item
) {
}
