package io.wisoft.ignoa_api.auth.dto;

public record AuthTokens(
        Long userId,
        String accessToken,
        String refreshToken
) {
}
