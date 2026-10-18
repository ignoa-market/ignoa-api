package io.wisoft.ignoa_api.bid.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BidHistoryCursorRequest(
        @Min(value = 1, message = "기준 입찰 ID는 1 이상이어야 합니다.")
        Long beforeBidId,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public BidHistoryCursorRequest {
        size = size == null ? 20 : size;
    }
}
