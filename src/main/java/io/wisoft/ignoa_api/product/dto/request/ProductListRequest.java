package io.wisoft.ignoa_api.product.dto.request;

import io.wisoft.ignoa_api.product.entity.ProductViewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProductListRequest(
        ProductViewType view,

        String category,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        Integer size
) {
    public ProductListRequest {
        view = view == null ? ProductViewType.ALL : view;
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}
