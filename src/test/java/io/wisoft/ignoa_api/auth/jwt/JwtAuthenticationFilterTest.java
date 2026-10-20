package io.wisoft.ignoa_api.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "test-access-token";

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    Claims claims;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_토큰이면_사용자_ID로_인증한다() throws Exception {
        // Given
        givenValidToken();

        // When
        doFilter();

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    void 서명이_유효하지_않은_토큰은_인증되지_않는다() throws Exception {
        // Given
        given(jwtTokenProvider.parseAccessToken(TOKEN)).willThrow(new JwtException("서명 불일치"));

        // When
        MockHttpServletResponse response = doFilter();

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void givenValidToken() {
        given(jwtTokenProvider.parseAccessToken(TOKEN)).willReturn(claims);
        given(claims.getSubject()).willReturn("1");
    }

    private MockHttpServletResponse doFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        return response;
    }
}
