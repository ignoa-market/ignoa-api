package io.wisoft.ignoa_api.chat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChatMessageCursorRequest(
        // 마지막으로 받은 메시지 ID. 없으면 최신부터
        @Min(value = 1, message = "커서는 1 이상이어야 합니다.")
        Long cursor,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size
) {
    public ChatMessageCursorRequest {
        size = size == null ? 30 : size;
    }
}
