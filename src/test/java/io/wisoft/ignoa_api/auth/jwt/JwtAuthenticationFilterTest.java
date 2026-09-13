package io.wisoft.ignoa_api.auth.jwt;

import io.jsonwebtoken.Claims;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    Claims claims;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Redis_연결_장애로_블랙리스트_조회가_실패해도_인증을_완료한다() {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));
        given(jwtTokenProvider.parseAccessToken(TOKEN)).willReturn(claims);
        given(claims.getSubject()).willReturn("1");

        // When
        assertThatCode(this::doFilter).doesNotThrowAnyException();

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
    }

    @Test
    void Redis_응답_지연으로_블랙리스트_조회가_실패해도_인증을_완료한다() {
        // Given
        given(tokenBlacklistService.isBlacklisted(anyString()))
                .willThrow(new QueryTimeoutException("Redis 명령 타임아웃"));
        given(jwtTokenProvider.parseAccessToken(TOKEN)).willReturn(claims);
        given(claims.getSubject()).willReturn("1");

        // When
        assertThatCode(this::doFilter).doesNotThrowAnyException();

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
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

    private void doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
