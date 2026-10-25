package io.wisoft.ignoa_api.user.dto.request;

import jakarta.validation.constraints.Min;

public record MyBidCursorRequest(
        @Min(value = 1, message = "커서는 1 이상이어야 합니다.")
        Long cursor
) {
}
