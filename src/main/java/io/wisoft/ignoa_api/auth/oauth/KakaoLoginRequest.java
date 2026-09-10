package io.wisoft.ignoa_api.auth.oauth;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank String code
) {
}
