package io.wisoft.ignoa_api.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointMatcherTest {

    private final PublicEndpointMatcher matcher = new PublicEndpointMatcher();

    @Test
    void GET_상품_조회는_공개_API다() {
        MockHttpServletRequest request = request("GET", "/api/items/1");

        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    void POST_상품_등록은_보호_API다() {
        MockHttpServletRequest request = request("POST", "/api/items");

        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    void 사용자_정보_조회는_보호_API다() {
        MockHttpServletRequest request = request("GET", "/api/users/me");

        assertThat(matcher.matches(request)).isFalse();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }
}
