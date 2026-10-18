package io.wisoft.ignoa_api.bid.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BidHistoryCursorRequest(
        // 마지막으로 받은 입찰 ID. 없으면 최신부터
        @Min(value = 1, message = "커서는 1 이상이어야 합니다.")
        Long cursor,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public BidHistoryCursorRequest {
        size = size == null ? 20 : size;
    }
}
