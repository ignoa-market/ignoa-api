package io.wisoft.ignoa_api.wish.dto.response;

import io.wisoft.ignoa_api.wish.entity.Wish;

import java.time.LocalDateTime;

public record WishSummary(
        Long wishId,
        Long itemId,
        String title,
        String category,
        Long currentPrice,
        LocalDateTime endAt,
        String mediaUrl,
        LocalDateTime wishedAt
) {
    public static WishSummary from(Wish wish, String mediaUrl) {
        return new WishSummary(
                wish.getId(),
                wish.getItem().getId(),
                wish.getItem().getTitle(),
                wish.getItem().getCategory(),
                wish.getItem().getCurrentPrice(),
                wish.getItem().getEndAt(),
                mediaUrl,
                wish.getCreatedAt()
        );
    }
}
