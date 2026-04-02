package io.wisoft.ignoa_api.bid.dto.request;

import jakarta.validation.constraints.Min;

public record BidListRequest(

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        Integer size
) {

    public BidListRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}
