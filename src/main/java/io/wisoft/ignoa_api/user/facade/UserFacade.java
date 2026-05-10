package io.wisoft.ignoa_api.user.facade;

import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.user.dto.response.UserMeResponse;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.event.ProfileImageDeletedEvent;
import io.wisoft.ignoa_api.user.service.UserCommandService;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserFacade {
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    public UserMeResponse updateProfileImage(Long userId, MultipartFile image) {
        // fetch user
        User user = userQueryService.findById(userId);

        // upload new image
        String imageUrl = storageService.upload(image);

        // delete old image (new image 업로드보다 반드시 뒤에 실행되어야 함)
        String oldImageUrl = user.getProfileImageUrl();
        if (oldImageUrl != null) {
            eventPublisher.publishEvent(new ProfileImageDeletedEvent(oldImageUrl));
        }

        // update user
        user.updateProfileImage(imageUrl);
        userCommandService.save(user);

        return UserMeResponse.from(user);
    }
}
