package io.wisoft.ignoa_api.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        String nickname,
        String address,

        @Size(max = 100, message = "자기소개는 100자 이하로 입력해주세요.")
        String bio
) {
}
