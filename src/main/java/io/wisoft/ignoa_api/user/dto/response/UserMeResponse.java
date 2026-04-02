package io.wisoft.ignoa_api.user.dto.response;

import io.wisoft.ignoa_api.user.entity.User;

public record UserMeResponse(
        Long userId,
        String email,
        String name,
        String address,
        String profileImageUrl
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getName(), user.getAddress(), user.getProfileImageUrl());
    }
}
