package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ItemDetail(
        Long itemId,
        SellerProfile seller,
        String title,
        String description,
        String category,
        String brand,
        ItemCondition itemCondition,
        Long startPrice,
        Long currentPrice,
        Long buyNowPrice,
        boolean isTopBidder,
        boolean isBidder,
        boolean isSeller,
        ItemStatus status,
        LocalDateTime createdAt,
        LocalDateTime endAt,
        Boolean isWished,
        Integer wishCount,
        Long viewCount,
        List<ItemMediaResponse> mediaUrls
) {
    public static ItemDetail of(
            Item item,
            SellerProfile seller,
            boolean isTopBidder,
            boolean isBidder,
            boolean isSeller,
            List<ItemMediaResponse> mediaUrls,
            int wishCount,
            boolean isWished
    ) {
        return new ItemDetail(
                item.getId(),
                seller,
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getBrand(),
                item.getItemCondition(),
                item.getStartPrice(),
                item.getCurrentPrice(),
                item.getBuyNowPrice(),
                isTopBidder,
                isBidder,
                isSeller,
                item.getStatus(),
                item.getCreatedAt(),
                item.getEndAt(),
                isWished,
                wishCount,
                item.getViewCount(),
                mediaUrls
        );
    }
}
