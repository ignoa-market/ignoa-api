package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemStatus;

import java.time.LocalDateTime;

public record ItemSummary(
        Long itemId,
        String title,
        String mediaUrl,
        Long currentPrice,
        Integer wishCount,
        Integer bidCount,
        ItemStatus status,
        LocalDateTime endAt
) {

    public static ItemSummary from(Item item, String mediaUrl, int wishCount, int bidCount) {
        return new ItemSummary(
                item.getId(),
                item.getTitle(),
                mediaUrl,
                item.getCurrentPrice(),
                wishCount,
                bidCount,
                item.getStatus(),
                item.getEndAt()
        );
    }
}
