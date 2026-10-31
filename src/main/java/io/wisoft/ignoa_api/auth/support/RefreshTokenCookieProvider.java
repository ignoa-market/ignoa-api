package io.wisoft.ignoa_api.auth.support;

import io.wisoft.ignoa_api.auth.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieProvider {

    private static final String COOKIE_NAME = "refresh_token";

    private final JwtProperties jwtProperties;

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofMillis(jwtProperties.refreshExpiration()))
                .build();
    }

    public ResponseCookie expire() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api");
    }
}
