package io.wisoft.ignoa_api.wish.dto.response;

import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.wish.entity.Wish;

import java.time.LocalDateTime;

public record WishPreview(
        Long wishId,
        Long itemId,
        String title,
        String category,
        Long currentPrice,
        Integer wishCount,
        ItemStatus itemStatus,
        LocalDateTime endAt,
        String mediaUrl,
        LocalDateTime wishedAt
) {
    public static WishPreview from(Wish wish, String mediaUrl, int wishCount, ItemStatus itemStatus) {
        return new WishPreview(
                wish.getId(),
                wish.getItem().getId(),
                wish.getItem().getTitle(),
                wish.getItem().getCategory(),
                wish.getItem().getCurrentPrice(),
                wishCount,
                itemStatus,
                wish.getItem().getEndAt(),
                mediaUrl,
                wish.getCreatedAt()
        );
    }
}
