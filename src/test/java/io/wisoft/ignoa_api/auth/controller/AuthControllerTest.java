package io.wisoft.ignoa_api.auth.controller;

import io.wisoft.ignoa_api.auth.oauth.KakaoAuthService;
import io.wisoft.ignoa_api.auth.service.AuthService;
import io.wisoft.ignoa_api.auth.service.EmailService;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;

    @Mock
    KakaoAuthService kakaoAuthService;

    @Mock
    EmailService emailService;

    @InjectMocks
    AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void Redis_장애로_로그아웃이_실패해도_RT_쿠키는_만료된다() throws Exception {
        // Given
        willThrow(new RedisConnectionFailureException("Redis 연결 실패"))
                .given(authService).logout(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token")
                        .cookie(new Cookie("refresh_token", "test-refresh-token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name()))
                .andExpect(cookie().value("refresh_token", ""))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }
}
