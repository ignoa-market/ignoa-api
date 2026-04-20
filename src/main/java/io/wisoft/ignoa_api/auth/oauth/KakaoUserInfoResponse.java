package io.wisoft.ignoa_api.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfoResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public String email() {
        return kakaoAccount().email();
    }

    public String nickname() {
        return kakaoAccount().profile().nickname();
    }

    public String profileImageUrl() {
        return kakaoAccount().profile().profileImageUrl();
    }

    public record KakaoAccount(
            String email,
            Profile profile
    ) {
        public record Profile(
                String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {}
    }
}
