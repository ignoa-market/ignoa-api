package io.wisoft.ignoa_api.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.exception.ErrorResponse;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import io.wisoft.ignoa_api.global.security.PublicEndpointMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;
    private final PublicEndpointMatcher publicEndpointMatcher;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                authenticate(token);

            } catch (RedisConnectionFailureException
                     | RedisSystemException
                     | RedisInfrastructureException e) {
                if (publicEndpointMatcher.matches(request)) {
                    log.warn(
                            "Redis 인프라 장애 - 공개 요청을 익명 처리: method={}, uri={}, reason={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            e.getClass().getSimpleName()
                    );

                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                log.error("Redis 인프라 장애 - 인증 차단: uri={}, reason={}",
                        request.getRequestURI(),
                        e.getClass().getSimpleName()
                );

                response.setStatus(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.getHttpStatus().value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                objectMapper.writeValue(
                        response.getWriter(),
                        ErrorResponse.of(ErrorCode.AUTH_INFRASTRUCTURE_ERROR)
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            long userId = Long.parseLong(jwtTokenProvider.parseAccessToken(token).getSubject());

            if (tokenBlacklistService.isBlacklisted(token)) {
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, List.of())
            );

        } catch (JwtException e) {
            log.debug("JWT 인증 실패: reason={}", e.getClass().getSimpleName());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
