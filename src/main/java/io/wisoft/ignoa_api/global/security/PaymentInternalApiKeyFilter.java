package io.wisoft.ignoa_api.global.security;

import io.wisoft.ignoa_api.trade.payment.PaymentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class PaymentInternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final byte[] apiKey;

    public PaymentInternalApiKeyFilter(PaymentProperties properties) {
        if (!StringUtils.hasText(properties.internalApiKey())) {
            throw new IllegalStateException("결제 서버 내부 API 키가 설정되지 않았습니다.");
        }

        apiKey = properties.internalApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !PATH_HELPER.getPathWithinApplication(request).startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestKey = request.getHeader(PaymentProperties.INTERNAL_API_KEY_HEADER);

        if (requestKey == null || !MessageDigest.isEqual(apiKey, requestKey.getBytes(StandardCharsets.UTF_8))) {
            log.warn("결제 서버 내부 API 키 검증 실패: method={}, uri={}, remoteAddress={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
