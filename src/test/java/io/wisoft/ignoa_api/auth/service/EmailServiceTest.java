package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyRequest;
import io.wisoft.ignoa_api.auth.dto.response.EmailVerifyResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String VERIFY_KEY = "email:verify:" + EMAIL;
    private static final String CODE = "123456";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, redisTemplate, userRepository);
    }

    @Test
    void 올바른_코드를_인증하면_같은_키가_인증_완료_상태로_전환된다() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(VERIFY_KEY)).willReturn(CODE);
        EmailVerifyRequest request = new EmailVerifyRequest(EMAIL, CODE);

        // When
        EmailVerifyResponse response = emailService.verifyEmailCode(request);

        // Then
        assertThat(response.email()).isEqualTo(EMAIL);
        verify(valueOperations).set(VERIFY_KEY, "VERIFIED", Duration.ofMinutes(10));
        verify(redisTemplate, never()).delete(VERIFY_KEY);
    }

    @Test
    void 인증에_사용한_코드는_다시_사용할_수_없다() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(VERIFY_KEY)).willReturn(CODE, "VERIFIED");
        EmailVerifyRequest request = new EmailVerifyRequest(EMAIL, CODE);
        emailService.verifyEmailCode(request);

        // When
        var exception = assertThatThrownBy(() -> emailService.verifyEmailCode(request));

        // Then
        exception.isInstanceOfSatisfying(
                BusinessException.class,
                businessException -> assertThat(businessException.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE)
        );
        verify(valueOperations, times(1))
                .set(VERIFY_KEY, "VERIFIED", Duration.ofMinutes(10));
    }

    @Test
    void 잘못된_코드를_입력하면_인증_상태를_변경하지_않는다() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(VERIFY_KEY)).willReturn(CODE);
        EmailVerifyRequest request = new EmailVerifyRequest(EMAIL, "654321");

        // When
        var exception = assertThatThrownBy(() -> emailService.verifyEmailCode(request));

        // Then
        exception.isInstanceOfSatisfying(
                BusinessException.class,
                businessException -> assertThat(businessException.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE)
        );
        verify(valueOperations, never())
                .set(VERIFY_KEY, "VERIFIED", Duration.ofMinutes(10));
    }

    @Test
    void 인증_완료_값일_때만_인증된_이메일로_판단한다() {
        // Given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(VERIFY_KEY)).willReturn("VERIFIED", CODE, null);

        // When & Then
        assertThat(emailService.isVerified(EMAIL)).isTrue();
        assertThat(emailService.isVerified(EMAIL)).isFalse();
        assertThat(emailService.isVerified(EMAIL)).isFalse();
    }

    @Test
    void 인증_상태를_삭제하면_인증에_사용한_동일한_키가_삭제된다() {
        // When
        emailService.deleteVerified(EMAIL);

        // Then
        verify(redisTemplate).delete(VERIFY_KEY);
    }
}
