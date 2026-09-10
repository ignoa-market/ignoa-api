package io.wisoft.ignoa_api.global.common;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class CookieUtils {

    private CookieUtils() {}

    public static ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofDays(7))
                .path("/api")
                .build();
    }

    public static ResponseCookie createClearRefreshTokenCookie() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .path("/api")
                .build();
    }
}
