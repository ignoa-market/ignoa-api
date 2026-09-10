package io.wisoft.ignoa_api.item.dto.request;

import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record ItemCreateRequest(
        @NotBlank(message = "상품 제목은 필수입니다")
        String title,

        @NotBlank(message = "상품 설명은 필수입니다")
        String description,

        @NotBlank(message = "카테고리는 필수입니다")
        String category,

        @NotNull(message = "상품 상태는 필수입니다")
        ItemCondition itemCondition,

        @NotNull(message = "시작 가격은 필수입니다")
        @Min(value = 0, message = "시작 가격은 0원 이상이어야 합니다")
        Long startPrice,

        @NotNull(message = "즉시 구매가는 필수입니다")
        @Min(value = 0, message = "즉시 구매가는 0원 이상이어야 합니다")
        Long buyNowPrice,

        @NotBlank(message = "브랜드명은 필수입니다")
        String brand,

        @NotNull(message = "경매 종료 시간은 필수입니다")
        @Future(message = "경매 종료 시간은 현재보다 미래여야 합니다")
        LocalDateTime endAt
) {

    @AssertTrue(message = "즉시 구매가는 시작 가격보다 커야 합니다")
    public boolean isBuyNowPriceValid() {
        if (startPrice == null || buyNowPrice == null) {
            return true;
        }
        return startPrice < buyNowPrice;
    }

    @AssertTrue(message = "경매 종료 시간은 최소 1일 이후여야 합니다")
    public boolean isEndAtNotTooSoon() {
        if (endAt == null) {
            return true;
        }
        return !endAt.isBefore(LocalDateTime.now().plusDays(1));
    }

    @AssertTrue(message = "경매 종료 시간은 최대 7일 이내여야 합니다")
    public boolean isEndAtNotTooLate() {
        if (endAt == null) {
            return true;
        }
        return !endAt.isAfter(LocalDateTime.now().plusDays(7));
    }
}
