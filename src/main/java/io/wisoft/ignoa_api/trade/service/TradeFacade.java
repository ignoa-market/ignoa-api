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

    private final PaymentResultApplier paymentResultApplier;
    private final TradePaymentService tradePaymentService;
    private final PaymentClient paymentClient;

    public TradePrepareResponse prepare(Long tradeId, Long buyerId) {
        PaymentPrepareRequest request = tradePaymentService.validatePrepare(tradeId, buyerId);
        PaymentPrepareResponse response = paymentClient.prepare(request);

        return TradePrepareResponse.from(response);
    }

    public TradeConfirmResponse confirm(Long tradeId, Long buyerId, TradeConfirmRequest request) {
        tradePaymentService.startConfirm(tradeId, buyerId, request.orderId());
        PaymentResult result;

        try {
            result = paymentClient.confirm(
                    new PaymentConfirmRequest(tradeId, request.orderId(), request.paymentKey(), request.amount())
            );

        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_CONFIRM_REJECTED) {
                tradePaymentService.cancelConfirm(tradeId, request.orderId());
            }
            throw e;
        }

        paymentResultApplier.apply(tradeId, result);
        return TradeConfirmResponse.from(result);
    }
}
