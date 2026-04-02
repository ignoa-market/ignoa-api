package io.wisoft.ignoa_api.product.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ProductCreateRequest(
        @NotBlank(message = "상품 제목은 필수입니다")
        String title,

        @NotBlank(message = "상품 설명은 필수입니다")
        String description,

        @NotBlank(message = "카테고리는 필수입니다")
        String category,

        @NotNull(message = "시작 가격은 필수입니다")
        @Min(value = 0, message = "시작 가격은 0원 이상이어야 합니다")
        Long startPrice,

        @NotNull(message = "경매 종료 시간은 필수입니다")
        @Future(message = "경매 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endTime
) {
}
