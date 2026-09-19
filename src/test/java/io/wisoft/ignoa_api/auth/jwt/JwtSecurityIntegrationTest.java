package io.wisoft.ignoa_api.auth.jwt;

import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.config.SecurityConfig;
import io.wisoft.ignoa_api.global.security.CloudFrontOriginFilter;
import io.wisoft.ignoa_api.global.security.PublicEndpointMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JwtSecurityIntegrationTest.TestController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        CloudFrontOriginFilter.class,
        PublicEndpointMatcher.class,
        JwtSecurityIntegrationTest.TestController.class
})
class JwtSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistService tokenBlacklistService;

    @Test
    void 공개_API는_Redis_장애_시_익명으로_컨트롤러까지_도달한다() throws Exception {
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        mockMvc.perform(get("/api/items/1")
                        .header("Authorization", "Bearer test-access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    void 보호_API는_Redis_장애_시_503으로_차단된다() throws Exception {
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        mockMvc.perform(get("/api/protected")
                        .header("Authorization", "Bearer test-access-token"))
                .andExpect(status().isServiceUnavailable());
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/items/{itemId}")
        String publicEndpoint(@AuthenticationPrincipal Long userId) {
            return userId == null ? "anonymous" : "authenticated";
        }

        @GetMapping("/api/protected")
        String protectedEndpoint() {
            return "protected";
        }
    }
}
