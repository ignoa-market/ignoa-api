package io.wisoft.ignoa_api.item.dto.request;

import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;
import java.util.List;

public record ItemUpdateRequest(
        String title,
        String description,
        String category,
        List<Long> deleteMediaIds,
        @Future(message = "경매 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endAt
) {
}
