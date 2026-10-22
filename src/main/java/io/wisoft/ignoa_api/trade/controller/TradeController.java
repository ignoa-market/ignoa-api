package io.wisoft.ignoa_api.trade.controller;

import io.wisoft.ignoa_api.global.common.ApiResponse;
import io.wisoft.ignoa_api.trade.dto.request.TradeConfirmRequest;
import io.wisoft.ignoa_api.trade.dto.response.MyTradeResponse;
import io.wisoft.ignoa_api.trade.dto.response.TradeConfirmResponse;
import io.wisoft.ignoa_api.trade.dto.response.TradePrepareResponse;
import io.wisoft.ignoa_api.trade.service.TradeFacade;
import io.wisoft.ignoa_api.trade.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TradeController {

    private final TradeFacade tradeFacade;
    private final TradeService tradeService;

    @PostMapping("/trades/{tradeId}/payments")
    public ResponseEntity<ApiResponse<TradePrepareResponse>> prepare(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long userId
    ) {
        TradePrepareResponse data = tradeFacade.prepare(tradeId, userId);
        ApiResponse<TradePrepareResponse> response = ApiResponse.of(data, "결제를 준비했습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/trades/{tradeId}/payments/confirm")
    public ResponseEntity<ApiResponse<TradeConfirmResponse>> confirm(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TradeConfirmRequest request
    ) {
        TradeConfirmResponse data = tradeFacade.confirm(tradeId, userId, request);
        ApiResponse<TradeConfirmResponse> response = ApiResponse.of(data, "결제 승인 요청을 처리했습니다.");
        return ResponseEntity.ok(response);
    }

    // 채팅방에서 낙찰자의 결제 대상 거래 조회
    @GetMapping("/items/{itemId}/trades/me")
    public ResponseEntity<ApiResponse<MyTradeResponse>> getMyAuctionTrade(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        MyTradeResponse data = tradeService.getMyAuctionTrade(itemId, userId);
        ApiResponse<MyTradeResponse> response = ApiResponse.of(data, "낙찰 거래를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trades/{tradeId}")
    public ResponseEntity<ApiResponse<MyTradeResponse>> getTrade(
            @PathVariable Long tradeId,
            @AuthenticationPrincipal Long userId
    ) {
        MyTradeResponse data = tradeService.getTrade(tradeId, userId);
        ApiResponse<MyTradeResponse> response = ApiResponse.of(data, "거래를 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
