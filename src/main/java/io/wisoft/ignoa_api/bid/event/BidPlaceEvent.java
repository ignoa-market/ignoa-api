package io.wisoft.ignoa_api.bid.event;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.user.entity.User;

import java.time.LocalDateTime;

public record BidPlaceEvent(
        Long itemId,
        Long currentPrice,
        String bidderNickname,
        LocalDateTime createdAt
) {

    public static BidPlaceEvent of(Bid bid, Item item, User user) {
        return new BidPlaceEvent(
                item.getId(),
                bid.getPrice(),
                user.getNickname(),
                bid.getCreatedAt());
    }
}
