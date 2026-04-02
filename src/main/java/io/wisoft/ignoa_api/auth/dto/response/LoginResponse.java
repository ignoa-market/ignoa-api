package io.wisoft.ignoa_api.auth.dto.response;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken
) {
}
