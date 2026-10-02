package io.wisoft.ignoa_api.trade.controller;

import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.service.TradePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class PaymentCallbackController {

    private final TradePaymentService tradePaymentService;

    @PostMapping("/trades/{tradeId}/payment-result")
    public ResponseEntity<Void> receive(
            @PathVariable Long tradeId,
            @RequestBody PaymentResult result
    ) {
        tradePaymentService.applyPaymentResult(tradeId, result);
        return ResponseEntity.ok().build();
    }
}
