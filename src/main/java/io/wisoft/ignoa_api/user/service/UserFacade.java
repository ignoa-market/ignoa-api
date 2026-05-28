package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.auth.service.RefreshTokenService;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.user.dto.response.MyProfile;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final StorageService storageService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    public MyProfile updateProfileImage(Long userId, MultipartFile image) {
        User user = userQueryService.findById(userId);

        String oldImageUrl = user.getProfileImageUrl();
        String newImageUrl = storageService.upload(image);
        user.updateProfileImage(newImageUrl);
        userCommandService.saveProfileImage(user, oldImageUrl);

        return MyProfile.from(user);
    }

    public void deleteMe(Long userId, String accessToken, String refreshToken) {
        userCommandService.withdraw(userId);
        tokenBlacklistService.blacklist(accessToken);
        refreshTokenService.delete(refreshToken);
    }
}
