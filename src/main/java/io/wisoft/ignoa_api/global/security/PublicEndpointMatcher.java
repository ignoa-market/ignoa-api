package io.wisoft.ignoa_api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

@Component
public class PublicEndpointMatcher implements RequestMatcher {

    private final RequestMatcher publicEndpoints;

    public PublicEndpointMatcher() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();

        this.publicEndpoints = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/auth/logout"),
                paths.matcher(HttpMethod.POST, "/api/auth/recover"),
                paths.matcher(HttpMethod.POST, "/api/auth/signup"),
                paths.matcher(HttpMethod.POST, "/api/auth/oauth/kakao"),
                paths.matcher(HttpMethod.POST, "/api/auth/refresh"),

                paths.matcher(HttpMethod.POST, "/api/auth/email/send"),
                paths.matcher(HttpMethod.POST, "/api/auth/email/verify"),
                paths.matcher(HttpMethod.GET, "/api/users/email/duplicate"),
                paths.matcher(HttpMethod.GET, "/api/users/nickname/duplicate"),

                paths.matcher(HttpMethod.GET, "/api/items"),
                paths.matcher(HttpMethod.GET, "/api/items/{itemId}"),
                paths.matcher(HttpMethod.GET, "/api/items/{itemId}/bids"),

                paths.matcher("/ws"),
                paths.matcher(HttpMethod.GET, "/actuator/**")
        );
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }
}
