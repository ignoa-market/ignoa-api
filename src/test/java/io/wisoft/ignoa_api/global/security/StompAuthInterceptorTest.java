package io.wisoft.ignoa_api.global.security;

import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock Claims claims;
    @Spy SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    @InjectMocks StompAuthInterceptor interceptor;

    @Test
    void Redis_장애로_STOMP_연결을_차단하면_지표를_남긴다() {
        when(jwtTokenProvider.parseAccessToken("token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(tokenBlacklistService.isBlacklisted("token"))
                .thenThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(meterRegistry.get("redis.auth.requests")
                .tags("entrypoint", "stomp", "outcome", "blocked").counter().count()).isEqualTo(1.0);
    }
}
