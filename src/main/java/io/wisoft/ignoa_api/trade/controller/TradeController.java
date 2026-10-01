package io.wisoft.ignoa_api.trade.controller;

import io.wisoft.ignoa_api.global.common.ApiResponse;
import io.wisoft.ignoa_api.trade.dto.response.TradePrepareResponse;
import io.wisoft.ignoa_api.trade.service.TradeFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades/{tradeId}/payments")
public class TradeController {

    private final TradeFacade tradeFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<TradePrepareResponse>> prepare(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long userId
    ) {
        TradePrepareResponse data = tradeFacade.prepare(tradeId, userId);
        ApiResponse<TradePrepareResponse> response = ApiResponse.of(data, "결제를 준비했습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
