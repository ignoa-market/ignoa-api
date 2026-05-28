package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;

import java.time.LocalDateTime;

public record ItemPreview(
        Long itemId,
        String brand,
        String title,
        String mediaUrl,
        Long currentPrice,
        Integer wishCount,
        Long viewCount,
        ItemStatus status,
        LocalDateTime endAt
) {

    public static ItemPreview from(Item item, String mediaUrl, int wishCount) {
        return new ItemPreview(
                item.getId(),
                item.getBrand(),
                item.getTitle(),
                mediaUrl,
                item.getCurrentPrice(),
                wishCount,
                item.getViewCount(),
                item.getStatus(),
                item.getEndAt()
        );
    }
}
