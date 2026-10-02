package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.trade.dto.request.TradeConfirmRequest;
import io.wisoft.ignoa_api.trade.dto.response.TradeConfirmResponse;
import io.wisoft.ignoa_api.trade.payment.PaymentClient;
import io.wisoft.ignoa_api.trade.dto.response.TradePrepareResponse;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentConfirmRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareResponse;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
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

    public TradeConfirmResponse confirm(Long tradeId, Long buyerId, TradeConfirmRequest request) {
        // Validate 하고 PAYMENT_PENDING 상태를 CONFIRMING로 바꾸는 작업
        tradeService.startConfirm(tradeId, buyerId, request.orderId());
        PaymentResult result;

        try {
            result = paymentClient.confirm(
                    new PaymentConfirmRequest(tradeId, request.orderId(), request.paymentKey(), request.amount())
            );

        } catch (BusinessException e) {
            // CONFIRMING을 PAYMENT_PENDING으로 되돌리는 작업
            if (e.getErrorCode() == ErrorCode.PAYMENT_CONFIRM_REJECTED) {
                tradeService.cancelConfirm(tradeId, request.orderId());
            }
            throw e;
        }

        tradeService.applyPaymentResult(tradeId, result);
        return TradeConfirmResponse.from(result);
    }
}
