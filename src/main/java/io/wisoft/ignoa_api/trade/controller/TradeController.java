package io.wisoft.ignoa_api.trade.controller;

import io.wisoft.ignoa_api.global.common.ApiResponse;
import io.wisoft.ignoa_api.trade.dto.request.TradeConfirmRequest;
import io.wisoft.ignoa_api.trade.dto.response.TradeConfirmResponse;
import io.wisoft.ignoa_api.trade.dto.response.TradePrepareResponse;
import io.wisoft.ignoa_api.trade.service.TradeFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<TradeConfirmResponse>> confirm(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TradeConfirmRequest request
    ) {
        TradeConfirmResponse data = tradeFacade.confirm(tradeId, userId, request);
        ApiResponse<TradeConfirmResponse> response = ApiResponse.of(data, "결제 승인 요청을 처리했습니다.");
        return ResponseEntity.ok(response);
    }
}
