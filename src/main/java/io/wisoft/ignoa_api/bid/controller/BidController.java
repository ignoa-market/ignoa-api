package io.wisoft.ignoa_api.bid.controller;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidHistory;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.service.BidFacade;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items/{itemId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final BidFacade bidFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @PathVariable Long itemId,
            @Valid @RequestBody BidCreateRequest request,
            @AuthenticationPrincipal Long bidderId
    ) {
        BidResponse data = bidFacade.placeBid(itemId, bidderId, request);
        ApiResponse<BidResponse> response = ApiResponse.of(data, "입찰에 성공하였습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BidHistory>>> getBids(
            @PathVariable Long itemId
    ) {
        List<BidHistory> data = bidService.getBids(itemId);
        ApiResponse<List<BidHistory>> response = ApiResponse.of(data, "입찰 내역을 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
