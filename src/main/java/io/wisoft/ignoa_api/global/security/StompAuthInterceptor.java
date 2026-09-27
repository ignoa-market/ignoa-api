package io.wisoft.ignoa_api.global.security;

import io.jsonwebtoken.JwtException;
import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String QUEUE_PREFIX = "/queue/";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SEND -> throw new MessageDeliveryException("클라이언트 SEND는 허용하지 않습니다.");
            case SUBSCRIBE -> rejectDirectQueueSubscription(accessor);
            default -> {
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        // 비로그인 사용자는 토큰 없이 연결한다 (입찰 방송 구독용). 이름표 없이 익명으로 통과시킨다.
        if (header == null) {
            return;
        }

        if (!header.startsWith(BEARER_PREFIX)) {
            log.debug("STOMP 인증 실패: reason=Bearer 형식 아님");
            throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            long userId = Long.parseLong(jwtTokenProvider.parseAccessToken(token).getSubject());

            if (tokenBlacklistService.isBlacklisted(token)) {
                log.debug("STOMP 인증 실패: userId={}, reason=블랙리스트 토큰", userId);
                throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("STOMP 인증 실패: reason={}", e.getClass().getSimpleName());
            throw new MessageDeliveryException("유효하지 않은 토큰입니다.");

        } catch (RedisConnectionFailureException
                 | RedisSystemException
                 | RedisInfrastructureException e) {
            log.warn("Redis 인프라 장애 - STOMP 연결 차단: reason={}", e.getClass().getSimpleName());
            throw new MessageDeliveryException("일시적인 오류로 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void rejectDirectQueueSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (destination != null && destination.startsWith(QUEUE_PREFIX)) {
            throw new MessageDeliveryException("개인 큐는 /user 주소로만 구독할 수 있습니다.");
        }
    }
}
