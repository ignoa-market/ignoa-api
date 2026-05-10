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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    public void purgeExpiredWithdrawals() {
        final int BATCH_SIZE = 500;
        int successCount = 0;
        int failCount = 0;
        int totalTargetCount = 0;

        Long lastId = 0L;

        LocalDate targetDate = LocalDate.now().minusDays(30);
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.plusDays(1).atStartOfDay();

        while (true) {
            List<User> users = userQueryService.findPurgeTargets(
                    startDateTime,
                    endDateTime,
                    lastId,
                    BATCH_SIZE
            );

            if (users.isEmpty()) {
                break;
            }

            for (User user : users) {
                lastId = user.getId();
                totalTargetCount++;

                try {
                    deleteOldImageUrl(user.getProfileImageUrl());
                    // wishRepository.deleteAllByUserId(user.getId());
                    user.purgePersonalData();
                    userCommandService.save(user);

                    successCount++;
                } catch (Exception e) {

                    failCount++;
                    log.warn("탈퇴 회원 개인정보 파기 실패 - userId: {}", user.getId(), e);

                    saveOutBox(user);
                }
            }
        }

        log.info("탈퇴 회원 개인정보 파기 작업 종료 - 대상 건수: {}, 성공 건수: {}, 실패 건수: {}", totalTargetCount, successCount, failCount);
    }

    private void deleteOldImageUrl(String user) {
        if (user != null) {
            eventPublisher.publishEvent(
                    new ProfileImageDeletedEvent(
                            user
                    )
            );
        }
    }

    /**
     * 아래 내용이 너무 복잡하면, 실패처리 된 것들만 익일 수동처리 하도록 해도됨.
     * <p>
     * 탈퇴처리에 실패한 케이스는 추후 범용 재처리 데이터(Outbox)로 저장한다.
     * 이러한 Outbox 데이터는 별도 처리하는 스케줄러에서 eventType에 따라 올바른 동작을 정의해준다.
     * <p>
     * 현재의 경우에서는 eventType == "PURGE_PERSONAL_DATA" 이면 개별 탈퇴처리하도록 해주면 될듯함.
     * 처리 후에는 OutboxStatus 변경필수
     */
    private static void saveOutBox(User user) {
        try {
            outboxRepository.save(
                    Outbox.builder()
                            .aggregateType("USER")
                            .aggregateId(String.valueOf(user.getId()))
                            .eventType("PURGE_PERSONAL_DATA")
                            .payload(
                                    """
                                            {
                                              "userId": %d
                                            }
                                            """.formatted(user.getId())
                            )
                            .status(OutboxStatus.PENDING)
                            .build()
            );
            log.info(
                    "Outbox 저장 완료 - userId: {}",
                    user.getId()
            );

        } catch (Exception outboxException) {
            log.error("Outbox 저장 실패 - userId: {}", user.getId(), outboxException);
        }
    }
}
