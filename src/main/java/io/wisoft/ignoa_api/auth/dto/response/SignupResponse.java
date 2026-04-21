package io.wisoft.ignoa_api.auth.dto.response;

public record SignupResponse(
        Long userId,
        String accessToken
) {
}
