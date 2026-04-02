package io.wisoft.ignoa_api.product.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record ProductUpdateRequest(
        String title,
        String description,
        String category,
        List<Long> deleteMediaIds,
        @Future(message = "경매 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endTime
) {
}
