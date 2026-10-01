package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.trade.payment.PaymentClient;
import io.wisoft.ignoa_api.trade.dto.response.TradePrepareResponse;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeFacade {

    private final TradeService tradeService;
    private final PaymentClient paymentClient;

    public TradePrepareResponse prepare(Long tradeId, Long buyerId) {
        PaymentPrepareRequest request = tradeService.validatePrepare(tradeId, buyerId);
        PaymentPrepareResponse response = paymentClient.prepare(request);
        return TradePrepareResponse.from(response);
    }
}
