package io.wisoft.ignoa_api.global.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 만료된_토큰은_401과_INVALID_TOKEN으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.name()));
    }

    @Test
    void Redis_연결_장애는_503과_AUTH_INFRASTRUCTURE_ERROR로_응답한다() throws Exception {
        mockMvc.perform(get("/test/redis-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name()));
    }

    @Test
    void Redis_인프라_장애는_503과_AUTH_INFRASTRUCTURE_ERROR로_응답한다() throws Exception {
        mockMvc.perform(get("/test/redis-infrastructure-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name()));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/expired-token")
        void expiredToken() {
            throw new ExpiredJwtException(null, null, "만료된 토큰");
        }

        @GetMapping("/test/redis-down")
        void redisDown() {
            throw new RedisConnectionFailureException("Redis 연결 실패");
        }

        @GetMapping("/test/redis-infrastructure-error")
        void redisInfrastructureError() {
            throw new RedisInfrastructureException("Redis 인프라 장애", new RuntimeException());
        }
    }
}
