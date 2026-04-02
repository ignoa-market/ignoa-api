package io.wisoft.ignoa_api.bid.event;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.user.entity.User;

import java.time.LocalDateTime;

public record BidPlaceEvent(
        Long productId,
        Long currentPrice,
        String bidderName,
        LocalDateTime createdAt
) {

    public static BidPlaceEvent of(Bid bid, Product product, User user) {
        return new BidPlaceEvent(
                product.getId(),
                bid.getPrice(),
                user.getName(),
                bid.getCreatedAt());
    }
}
