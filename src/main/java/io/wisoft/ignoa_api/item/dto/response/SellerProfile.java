package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.user.entity.User;

public record SellerProfile(
        Long sellerId,
        String nickname,
        String profileImageUrl,
        String address
) {
    public static SellerProfile from(User user, String profileImageUrl) {
        return new SellerProfile(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                summarizeAddress(user.getAddress())
        );
    }

    private static String summarizeAddress(String address) {
        if (address == null) return null;
        String[] parts = address.split(" ");
        if (parts.length < 2) return address;
        String city = parts[0]
                .replace("특별시", "").replace("광역시", "")
                .replace("특별자치도", "").replace("특별자치시", "").replace("도", "");
        return city + " " + parts[1];
    }
}
