package io.wisoft.ignoa_api.trade.controller;

import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.service.PaymentResultApplier;
import io.wisoft.ignoa_api.trade.service.TradePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class PaymentCallbackController {

    private final PaymentResultApplier paymentResultApplier;

    @PostMapping("/trades/{tradeId}/payment-result")
    public ResponseEntity<Void> receive(
            @PathVariable Long tradeId,
            @RequestBody PaymentResult result
    ) {
        log.debug("결제 결과 콜백 수신: tradeId={}, orderId={}, status={}", tradeId, result.orderId(), result.status());
        paymentResultApplier.apply(tradeId, result);
        return ResponseEntity.ok().build();
    }
}
