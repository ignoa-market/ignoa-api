package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.auth.service.RefreshTokenService;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
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

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    private final StorageService storageService;
    private final OutboxAppender outboxAppender;

    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    public MyProfile updateProfileImage(Long userId, MultipartFile image) {
        User user = userQueryService.findById(userId);

        String oldImageUrl = user.getProfileImageUrl();
        String newImageUrl = storageService.upload(image);

        user.updateProfileImage(newImageUrl);

        try {
            userCommandService.saveProfileImage(user, oldImageUrl);
        } catch (RuntimeException e) {
            compensate(userId.toString(), newImageUrl);
            throw e;
        }

        return MyProfile.from(user);
    }

    private void compensate(String userId, String mediaUrl) {
        try {
            outboxAppender.saveForCompensation(userId, "USER", mediaUrl, OutboxEventType.DELETE_PROFILE_IMAGE);
        } catch (RuntimeException compensateError) {
            log.error("보상 Outbox 적재 실패 - 고아 파일 수동 정리 필요 - userId={}, mediaUrl={}", userId, mediaUrl, compensateError);
        }
    }

    public void deleteMe(Long userId, String accessToken, String refreshToken) {
        userCommandService.withdraw(userId);
        tokenBlacklistService.blacklist(accessToken);
        refreshTokenService.delete(refreshToken);
    }
}
