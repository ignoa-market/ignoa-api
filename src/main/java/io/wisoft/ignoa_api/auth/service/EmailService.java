package io.wisoft.ignoa_api.auth.service;


import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyCodeRequest;
import io.wisoft.ignoa_api.auth.dto.request.EmailVerifyRequest;
import io.wisoft.ignoa_api.auth.dto.response.EmailVerifyResponse;
import io.wisoft.ignoa_api.auth.support.EmailTemplateBuilder;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFY_PREFIX = "email:verify:";
    private static final String VERIFIED_PREFIX = "email:verified:";
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    public void sendEmailCode(EmailVerifyCodeRequest request) {
        String email = request.email();
        String domain = email.split("@")[1];

        if (!VALID_DOMAINS.contains(domain)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }

        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        redisTemplate.opsForValue().set(VERIFY_PREFIX + email, code, Duration.ofMinutes(5));
        send(email, "[Ignoa] 이메일 인증 코드", EmailTemplateBuilder.buildVerificationEmail(code));
    }

    public EmailVerifyResponse verifyEmailCode(EmailVerifyRequest request) {
        String email = request.email();
        String savedCode = redisTemplate.opsForValue().get(VERIFY_PREFIX + email);

        if (!request.code().equals(savedCode)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        redisTemplate.opsForValue().set(VERIFIED_PREFIX + email, "true", Duration.ofMinutes(10));
        redisTemplate.delete(VERIFY_PREFIX + email);

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
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public boolean isVerified(String email) {
        return redisTemplate.opsForValue().get(VERIFIED_PREFIX + email) != null;
    }

    public void deleteVerified(String email) {
        redisTemplate.delete(VERIFIED_PREFIX + email);
    }
}
