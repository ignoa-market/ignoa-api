package io.wisoft.ignoa_api.user.dto.request;

public record UpdateUserRequest(
        String nickname,
        String address
) {
}
