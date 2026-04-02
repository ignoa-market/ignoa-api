package io.wisoft.ignoa_api.auth.dto.response;

public record RefreshResponse(
        String accessToken,
        String refreshToken
) {
}
