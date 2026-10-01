package io.wisoft.ignoa_api.trade.payment;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareResponse;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentServerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
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
            log.error("결제 서버 요청 실패: operation=PREPARE, tradeId={}", request.tradeId(), e);
            throw new BusinessException(ErrorCode.PAYMENT_SERVER_ERROR);
        }
    }
}
