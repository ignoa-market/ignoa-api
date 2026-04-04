package io.wisoft.ignoa_api.auth.controller;

import io.wisoft.ignoa_api.auth.dto.request.*;
import io.wisoft.ignoa_api.auth.dto.response.EmailVerifyResponse;
import io.wisoft.ignoa_api.auth.dto.response.LoginResponse;
import io.wisoft.ignoa_api.auth.dto.response.RefreshResponse;
import io.wisoft.ignoa_api.auth.dto.response.SignupResponse;
import io.wisoft.ignoa_api.auth.service.AuthService;
import io.wisoft.ignoa_api.auth.service.EmailService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse data = authService.signup(request);
        ApiResponse<SignupResponse> response = ApiResponse.of(data, "회원가입이 완료되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse data = authService.login(request);
        ApiResponse<LoginResponse> response = ApiResponse.of(data, "로그인이 완료되었습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Refresh-Token") String refreshToken) {
        authService.logout(refreshToken);
        ApiResponse<Void> response = ApiResponse.of(null, "로그아웃이 완료되었습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse data = authService.refresh(request);
        ApiResponse<RefreshResponse> response = ApiResponse.of(data, "액세스 토큰이 재발급되었습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(@Valid @RequestBody EmailVerifyCodeRequest request) {
        emailService.sendEmailCode(request);
        ApiResponse<Void> response = ApiResponse.of(null, "이메일 인증 코드를 보냈습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        EmailVerifyResponse data = emailService.verifyEmailCode(request);
        ApiResponse<EmailVerifyResponse> response = ApiResponse.of(data, "이메일 인증에 성공했습니다.");
        return ResponseEntity.ok(response);
    }
}
