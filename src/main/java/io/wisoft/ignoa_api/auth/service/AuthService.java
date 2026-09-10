package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.dto.AuthTokens;
import io.wisoft.ignoa_api.auth.dto.request.LoginRequest;
import io.wisoft.ignoa_api.auth.dto.request.SignupRequest;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthTokens signup(SignupRequest request) {
        String email = request.email();

        if (!emailService.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.address()
        );
        userRepository.save(user);

        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenService.save(refreshToken, userId);
        emailService.deleteVerified(email);

        return new AuthTokens(userId, accessToken, refreshToken);
    }

    public AuthTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING_DELETION);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenService.save(refreshToken, userId);

        return new AuthTokens(userId, accessToken, refreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        jwtTokenProvider.parseRefreshToken(refreshToken);
        tokenBlacklistService.blacklist(accessToken);
        refreshTokenService.delete(refreshToken);
    }

    public AuthTokens refresh(String token) {
        jwtTokenProvider.parseRefreshToken(token);

        Long consumedUserId = refreshTokenService.consumeToken(token);

        if (consumedUserId == null) {
            long userId = Long.parseLong(jwtTokenProvider.parseRefreshToken(token).getSubject());
            refreshTokenService.deleteAllByUserId(userId);
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(consumedUserId);
        String refreshToken = jwtTokenProvider.createRefreshToken(consumedUserId);
        refreshTokenService.save(refreshToken, consumedUserId);

        return new AuthTokens(consumedUserId, accessToken, refreshToken);
    }

    @Transactional
    public AuthTokens recover(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.isDeleted()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_RECOVERABLE);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.restore();

        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(refreshToken, userId);

        return new AuthTokens(userId, accessToken, refreshToken);
    }
}

