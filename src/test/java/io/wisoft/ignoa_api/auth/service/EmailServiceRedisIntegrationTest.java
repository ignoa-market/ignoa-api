package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyRequest;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailServiceRedisIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String VERIFY_KEY = "email:verify:" + EMAIL;
    private static final String CODE = "123456";

    @Autowired
    private EmailService emailService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(VERIFY_KEY);
    }

    @Test
    void 인증_코드는_한_번만_사용되고_인증_완료_상태는_10분간_유지된다() {
        // Given
        redisTemplate.opsForValue().set(VERIFY_KEY, CODE, Duration.ofMinutes(5));
        EmailVerifyRequest request = new EmailVerifyRequest(EMAIL, CODE);

        // When
        emailService.verifyEmailCode(request);

        // Then
        assertThat(redisTemplate.opsForValue().get(VERIFY_KEY)).isEqualTo("VERIFIED");
        assertThat(redisTemplate.getExpire(VERIFY_KEY, TimeUnit.SECONDS))
                .isBetween(590L, 600L);

        assertThatThrownBy(() -> emailService.verifyEmailCode(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE)
                );
    }
}
