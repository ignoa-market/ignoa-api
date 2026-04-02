package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auth.dto.request.LoginRequest;
import io.wisoft.ignoa_api.auth.dto.request.RefreshRequest;
import io.wisoft.ignoa_api.auth.dto.request.SignupRequest;
import io.wisoft.ignoa_api.auth.dto.response.LoginResponse;
import io.wisoft.ignoa_api.auth.dto.response.RefreshResponse;
import io.wisoft.ignoa_api.auth.dto.response.SignupResponse;
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

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email();

        if (!emailService.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.name(),
                request.address()
        );
        userRepository.save(user);

        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(refreshToken, userId);

        emailService.deleteVerified(email);

        return new SignupResponse(userId, accessToken, refreshToken);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(refreshToken, userId);

        return new LoginResponse(userId, accessToken, refreshToken);
    }

    public void logout(String refreshToken) {
        jwtTokenProvider.parseToken(refreshToken);
        refreshTokenService.delete(refreshToken);
    }

    public RefreshResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        jwtTokenProvider.parseToken(token);
        Long userId = refreshTokenService.getUserId(token);

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        refreshTokenService.delete(token);
        refreshTokenService.save(refreshToken, userId);

        return new RefreshResponse(accessToken, refreshToken);
    }
}
