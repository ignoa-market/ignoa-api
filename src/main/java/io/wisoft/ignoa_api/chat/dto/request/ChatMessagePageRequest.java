package io.wisoft.ignoa_api.chat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChatMessagePageRequest(
        @Min(value = 1, message = "기준 메시지 ID는 1 이상이어야 합니다.")
        Long beforeMessageId,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size
) {
    public ChatMessagePageRequest {
        size = size == null ? 30 : size;
    }
}
