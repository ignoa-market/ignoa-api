package io.wisoft.ignoa_api.user.dto.response;

import io.wisoft.ignoa_api.user.entity.User;

public record MyProfile(
        Long userId,
        String email,
        String nickname,
        String address,
        String profileImageUrl
) {
    public static MyProfile from(User user) {
        return new MyProfile(user.getId(), user.getEmail(), user.getNickname(), user.getAddress(), user.getProfileImageUrl());
    }
}