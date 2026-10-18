package io.wisoft.ignoa_api.item.dto.request;

import io.wisoft.ignoa_api.item.entity.enums.ItemViewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ItemPreviewCursorRequest(
        ItemViewType view,

        @Min(value = 1, message = "커서는 1 이상이어야 합니다.")
        Long cursor,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {
    public ItemPreviewCursorRequest {
        view = view == null ? ItemViewType.LATEST : view;
        size = size == null ? 10 : size;
    }
}
