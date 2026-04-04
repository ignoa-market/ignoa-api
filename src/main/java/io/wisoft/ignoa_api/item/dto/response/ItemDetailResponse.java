package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ItemDetailResponse(
        Long itemId,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        String category,
        Long startPrice,
        Long currentPrice,
        boolean isTopBidder,
        ItemStatus status,
        LocalDateTime createdAt,
        LocalDateTime endAt,
        Boolean isWished,
        Integer wishCount,
        Integer bidCount,
        List<ItemMediaInfo> mediaUrls
) {
    public static ItemDetailResponse of(
            Item item,
            boolean isTopBidder,
            List<ItemMediaInfo> mediaUrls,
            int wishCount,
            int bidCount,
            boolean isWished
    ) {
        return new ItemDetailResponse(
                item.getId(),
                item.getSeller().getId(),
                item.getSeller().getNickname(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getStartPrice(),
                item.getCurrentPrice(),
                isTopBidder,
                item.getStatus(),
                item.getCreatedAt(),
                item.getEndAt(),
                isWished,
                wishCount,
                bidCount,
                mediaUrls
        );
    }
}
