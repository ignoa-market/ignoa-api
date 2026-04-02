package io.wisoft.ignoa_api.wish.dto.response;

import io.wisoft.ignoa_api.wish.entity.Wish;

import java.time.LocalDateTime;

public record WishSummary(
        Long wishId,
        Long productId,
        String title,
        String category,
        Long currentPrice,
        LocalDateTime endTime,
        String mediaUrl,
        LocalDateTime wishedAt
) {
    public static WishSummary from(Wish wish, String mediaUrl) {
        return new WishSummary(
                wish.getId(),
                wish.getProduct().getId(),
                wish.getProduct().getTitle(),
                wish.getProduct().getCategory(),
                wish.getProduct().getCurrentPrice(),
                wish.getProduct().getEndTime(),
                mediaUrl,
                wish.getCreatedAt()
        );
    }
}
