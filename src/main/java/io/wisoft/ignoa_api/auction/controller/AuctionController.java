package io.wisoft.ignoa_api.auction.controller;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuctionController {

    private final AuctionFacade auctionFacade;

    @PostMapping("/items/{itemId}/extend")
    public ResponseEntity<ApiResponse<AuctionExtensionResponse>> extendAuction(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        AuctionExtensionResponse data = auctionFacade.extendAuction(itemId, userId);
        ApiResponse<AuctionExtensionResponse> response = ApiResponse.of(data, "경매 마감을 연장했습니다.");
        return ResponseEntity.ok(response);
    }
}
