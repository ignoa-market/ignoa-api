package io.wisoft.ignoa_api.global.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.jsonwebtoken.ExpiredJwtException;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
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
        try (LogCapture logs = new LogCapture()) {
            mockMvc.perform(get("/test/redis-down"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name()));
            assertThat(logs.events()).singleElement()
                    .extracting(ILoggingEvent::getLevel).isEqualTo(Level.DEBUG);
        }
    }

    @Test
    void 분산_락_인프라_장애는_503으로_응답하되_요청별_ERROR를_남기지_않는다() throws Exception {
        try (LogCapture logs = new LogCapture()) {
            mockMvc.perform(get("/test/lock-infrastructure-error"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(ErrorCode.LOCK_INFRASTRUCTURE_ERROR.name()));
            assertThat(logs.events()).singleElement()
                    .extracting(ILoggingEvent::getLevel).isEqualTo(Level.DEBUG);
        }
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

        @GetMapping("/test/lock-infrastructure-error")
        void lockInfrastructureError() {
            throw new BusinessException(ErrorCode.LOCK_INFRASTRUCTURE_ERROR);
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        private final Level previousLevel = logger.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture() {
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);
        }

        private java.util.List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
