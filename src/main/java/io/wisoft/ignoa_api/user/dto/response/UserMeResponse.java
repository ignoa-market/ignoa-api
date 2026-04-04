package io.wisoft.ignoa_api.user.dto.response;

import io.wisoft.ignoa_api.user.entity.User;

public record UserMeResponse(
        Long userId,
        String email,
        String nickname,
        String address,
        String profileImageUrl
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getAddress(), user.getProfileImageUrl());
    }
}
