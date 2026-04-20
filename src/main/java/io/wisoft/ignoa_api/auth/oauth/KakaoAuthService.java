package io.wisoft.ignoa_api.auth.oauth;

import io.wisoft.ignoa_api.auth.dto.AuthTokens;
import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import io.wisoft.ignoa_api.auth.service.RefreshTokenService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoAuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    private final UserRepository userRepository;

    @Transactional
    public AuthTokens login(String code) {
        // 1. 인가 코드로 카카오 AT 교환
        String kakaoAccessToken = kakaoOAuthClient.getAccessToken(code);

        // 2. 카카오 AT로 사용자 정보 조회
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(kakaoAccessToken);

        // 3. 이메일로 기존 유저 조회 -> 없으면 자동 가입
        User user = userRepository.findByEmail(userInfo.email())
                .orElseGet(() -> userRepository.save(User.ofKakao(
                        userInfo.email(),
                        userInfo.nickname(),
                        userInfo.profileImageUrl(),
                        String.valueOf(userInfo.id())))
                );

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING_DELETION);
        }

        // 4. JWT 발급
        Long userId = user.getId();
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken((userId));
        refreshTokenService.save(refreshToken, userId);

        // 5. AuthTokens 발급
        return new AuthTokens(userId, accessToken, refreshToken);
    }
}
