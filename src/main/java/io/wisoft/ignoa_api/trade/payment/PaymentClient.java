package io.wisoft.ignoa_api.trade.payment;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.trade.payment.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentClient {

    private final RestClient paymentRestClient;

    public PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        try {
            PaymentServerResponse<PaymentPrepareResponse> response = paymentRestClient.post()
                    .uri("/internal/payments")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return response.data();

        } catch (RestClientException e) {
            log.warn(
                    "결제 준비 요청 실패: tradeId={}, errorType={}",
                    request.tradeId(),
                    e.getClass().getSimpleName()
            );
            throw new BusinessException(ErrorCode.PAYMENT_SERVER_ERROR);
        }
    }

    public PaymentResult confirm(PaymentConfirmRequest request) {
        try {
            PaymentServerResponse<PaymentResult> response = paymentRestClient.post()
                    .uri("/internal/payments/confirm")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return response.data();

        } catch (HttpClientErrorException e) {
            log.debug(
                    "결제 서버의 승인 요청 거절: tradeId={}, orderId={}, httpStatus={}",
                    request.tradeId(),
                    request.orderId(),
                    e.getStatusCode()
            );

            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_REJECTED);

        } catch (RestClientException e) {
            log.warn(
                    "결제 승인 결과 미확인: tradeId={}, orderId={}, errorType={}, action=결제 결과 재확인 대기",
                    request.tradeId(),
                    request.orderId(),
                    e.getClass().getSimpleName()
            );

            return PaymentResult.unknown(request);
        }
    }

    public PaymentResult getPayment(String orderId) {
        PaymentServerResponse<PaymentResult> response = paymentRestClient.get()
                .uri("/internal/payments/{orderId}", orderId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return response.data();
    }
}
