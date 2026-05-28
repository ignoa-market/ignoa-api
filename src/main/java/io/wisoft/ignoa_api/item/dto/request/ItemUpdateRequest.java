package io.wisoft.ignoa_api.item.dto.request;

import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.List;

public record ItemUpdateRequest(
        String title,
        String description,
        String category,
        String brand,
        ItemCondition itemCondition,
        @Min(value = 0, message = "즉시 구매가는 0원 이상이어야 합니다")
        Long buyNowPrice,
        List<Long> deleteMediaIds,
        @Future(message = "경매 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endAt
) {
}
