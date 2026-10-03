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
            log.warn("결제 준비 실패: tradeId={}, reason=결제 서버 응답 실패, 구매자 재시도 가능", request.tradeId(), e);
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
            log.debug("결제 승인 거절: tradeId={}, orderId={}, status={}, reason=결제 서버가 Toss 호출 전에 거절",
                    request.tradeId(), request.orderId(), e.getStatusCode());

            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_REJECTED);

        } catch (RestClientException e) {
            log.warn("결제 승인 결과 미확인: tradeId={}, orderId={}, reason=결제 서버 응답 없음, 콜백으로 확정 예정",
                    request.tradeId(), request.orderId(), e);

            return PaymentResult.unknown(request);
        }
    }
}
