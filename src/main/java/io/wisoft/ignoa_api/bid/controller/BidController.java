package io.wisoft.ignoa_api.bid.controller;

import io.wisoft.ignoa_api.bid.dto.request.BidListRequest;
import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidSummary;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.global.dto.ApiResponse;
import io.wisoft.ignoa_api.global.dto.SliceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @PostMapping
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @PathVariable Long productId,
            @Valid @RequestBody BidCreateRequest request,
            @AuthenticationPrincipal Long bidderId
    ) {
        BidResponse data = bidService.placeBid(productId, bidderId, request);
        ApiResponse<BidResponse> response = ApiResponse.of(data, "입찰에 성공하였습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<BidSummary>>> getBids(
            @PathVariable Long productId,
            @Valid @ModelAttribute BidListRequest request
    ) {
        SliceResponse<BidSummary> data = bidService.getBids(productId, request);
        ApiResponse<SliceResponse<BidSummary>> response = ApiResponse.of(data, "입찰 내역을 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
