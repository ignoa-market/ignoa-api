package io.wisoft.ignoa_api.auth.service;


import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyCodeRequest;
import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyRequest;
import io.wisoft.ignoa_api.auth.dto.response.EmailVerifyResponse;
import io.wisoft.ignoa_api.auth.support.EmailTemplateBuilder;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFY_PREFIX = "email:verify:";
    private static final String VERIFIED_VALUE = "VERIFIED";
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> VERIFY_EMAIL_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('GET', KEYS[1])

                    if current ~= ARGV[2] and current == ARGV[1] then
                        redis.call(
                            'SET',
                            KEYS[1],
                            ARGV[2],
                            'EX',
                            tonumber(ARGV[3])
                        )
                        return 1
                    end

                    return 0
                    """, Long.class);

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public void sendEmailCode(EmailVerifyCodeRequest request) {
        String email = request.email();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        redisTemplate.opsForValue().set(VERIFY_PREFIX + email, code, Duration.ofMinutes(5));
        send(email, "[Ignoa] 이메일 인증 코드", EmailTemplateBuilder.buildVerificationEmail(code));
    }

    public EmailVerifyResponse verifyEmailCode(EmailVerifyRequest request) {
        String email = request.email();
        String key = VERIFY_PREFIX + email;

        Long result = redisTemplate.execute(
                VERIFY_EMAIL_SCRIPT,
                List.of(key),
                request.code(),
                VERIFIED_VALUE,
                String.valueOf(VERIFIED_TTL.toSeconds())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        return new EmailVerifyResponse(email);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, exception);
        }
    }

    public boolean isVerified(String email) {
        return VERIFIED_VALUE.equals(
                redisTemplate.opsForValue().get(VERIFY_PREFIX + email)
        );
    }

    public void deleteVerified(String email) {
        redisTemplate.delete(VERIFY_PREFIX + email);
    }
}
