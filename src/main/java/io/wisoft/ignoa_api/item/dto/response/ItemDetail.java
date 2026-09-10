package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ItemDetail(
        Long itemId,
        String title,
        String description,
        String category,
        String brand,
        ItemCondition itemCondition,
        List<ItemMediaUrls> mediaUrls,

        SellerProfile seller,

        Long startPrice,
        Long currentPrice,
        Long buyNowPrice,
        ItemStatus status,
        LocalDateTime createdAt,
        LocalDateTime endAt,

        boolean isTopBidder,
        boolean isBidder,
        boolean isSeller,
        boolean isWished,
        int wishCount
) {
    public static ItemDetail of(
            Item item,
            List<ItemMediaUrls> mediaUrls,
            SellerProfile seller,
            boolean isTopBidder,
            boolean isBidder,
            boolean isSeller,
            boolean isWished,
            int wishCount
    ) {
        return new ItemDetail(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getBrand(),
                item.getItemCondition(),
                mediaUrls,
                seller,
                item.getStartPrice(),
                item.getCurrentPrice(),
                item.getBuyNowPrice(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getEndAt(),
                isTopBidder,
                isBidder,
                isSeller,
                isWished,
                wishCount
        );
    }
}
