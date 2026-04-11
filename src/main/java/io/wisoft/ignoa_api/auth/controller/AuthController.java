package io.wisoft.ignoa_api.auth.controller;

import io.wisoft.ignoa_api.auth.dto.AuthTokens;
import io.wisoft.ignoa_api.auth.dto.request.*;
import io.wisoft.ignoa_api.auth.dto.response.EmailVerifyResponse;
import io.wisoft.ignoa_api.auth.dto.response.LoginResponse;
import io.wisoft.ignoa_api.auth.dto.response.RefreshResponse;
import io.wisoft.ignoa_api.auth.dto.response.SignupResponse;
import io.wisoft.ignoa_api.auth.service.AuthService;
import io.wisoft.ignoa_api.auth.service.EmailService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.signup(request);
        ResponseCookie cookie = createRefreshTokenCookie(tokens.refreshToken());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        SignupResponse data = new SignupResponse(tokens.userId(), tokens.accessToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(data, "회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.login(request);
        ResponseCookie cookie = createRefreshTokenCookie(tokens.refreshToken());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        LoginResponse data = new LoginResponse(tokens.userId(), tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.of(data, "로그인이 완료되었습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @CookieValue("refresh_token") String refreshToken
    ) {
        authService.logout(authHeader.substring(7), refreshToken);
        return ResponseEntity.ok(ApiResponse.of(null, "로그아웃이 완료되었습니다."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue("refresh_token") String refreshToken,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.refresh(refreshToken);
        ResponseCookie cookie = createRefreshTokenCookie(tokens.refreshToken());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        RefreshResponse data = new RefreshResponse(tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.of(data, "액세스 토큰이 재발급되었습니다."));
    }

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(
            @Valid @RequestBody EmailVerifyCodeRequest request
    ) {
        emailService.sendEmailCode(request);
        ApiResponse<Void> response = ApiResponse.of(null, "이메일 인증 코드를 보냈습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyEmailCode(
            @Valid @RequestBody EmailVerifyRequest request
    ) {
        EmailVerifyResponse data = emailService.verifyEmailCode(request);
        ApiResponse<EmailVerifyResponse> response = ApiResponse.of(data, "이메일 인증에 성공했습니다.");
        return ResponseEntity.ok(response);
    }

    private static ResponseCookie createRefreshTokenCookie(String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(Duration.ofDays(7))
                .path("/api/auth")
                .build();
        return cookie;
    }
}
