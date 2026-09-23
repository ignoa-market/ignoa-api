package io.wisoft.ignoa_api.auth.jwt;

import io.jsonwebtoken.Claims;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import io.wisoft.ignoa_api.global.security.PublicEndpointMatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "test-access-token";

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    TokenBlacklistService tokenBlacklistService;

    @Mock
    PublicEndpointMatcher publicEndpointMatcher;

    @Mock
    Claims claims;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Redis_연결_장애로_블랙리스트_조회가_실패하면_503으로_차단한다() throws Exception {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        // When
        MockHttpServletResponse response = doFilter();

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentAsString())
                .contains(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Redis_응답_지연으로_블랙리스트_조회가_실패하면_503으로_차단한다() throws Exception {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisInfrastructureException(
                        "Redis 명령 시간 초과",
                        new QueryTimeoutException("Redis 명령 타임아웃")
                ));

        // When
        MockHttpServletResponse response = doFilter();

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentAsString())
                .contains(ErrorCode.AUTH_INFRASTRUCTURE_ERROR.name());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Redis_장애로_차단된_요청은_이후_필터로_진입하지_않는다() throws Exception {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockFilterChain filterChain = new MockFilterChain();

        // When
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // Then
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void 공개_API는_Redis_장애가_발생해도_익명으로_요청을_계속한다() throws Exception {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));
        given(publicEndpointMatcher.matches(any(HttpServletRequest.class)))
                .willReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        assertThat(filterChain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 블랙리스트에_등록된_토큰은_인증되지_않는다() throws Exception {
        // Given
        given(tokenBlacklistService.isBlacklisted(TOKEN)).willReturn(true);

        // When
        doFilter();

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletResponse doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        return response;
    }
}
