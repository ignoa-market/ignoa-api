package io.wisoft.ignoa_api.wish.dto.response;

import io.wisoft.ignoa_api.wish.entity.Wish;

import java.time.LocalDateTime;

public record WishPreview(
        Long wishId,
        Long itemId,
        String title,
        String category,
        Long currentPrice,
        Integer wishCount,
        LocalDateTime endAt,
        String mediaUrl,
        LocalDateTime wishedAt
) {
    public static WishPreview from(Wish wish, String mediaUrl, int wishCount) {
        return new WishPreview(
                wish.getId(),
                wish.getItem().getId(),
                wish.getItem().getTitle(),
                wish.getItem().getCategory(),
                wish.getItem().getCurrentPrice(),
                wishCount,
                wish.getItem().getEndAt(),
                mediaUrl,
                wish.getCreatedAt()
        );
    }
}
