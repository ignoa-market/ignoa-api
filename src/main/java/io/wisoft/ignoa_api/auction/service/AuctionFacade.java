package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionFacade {

    private final AuctionService auctionService;

    public boolean closeAuction(Long itemId) {
        return auctionService.closeAuction(itemId);
    }

    public AuctionExtensionResponse extendAuction(Long itemId, Long userId) {
        return auctionService.extendAuction(itemId, userId);
    }
}
