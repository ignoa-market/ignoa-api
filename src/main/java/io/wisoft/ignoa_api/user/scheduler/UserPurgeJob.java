package io.wisoft.ignoa_api.user.scheduler;

import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserCommandService;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeJob {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    public void execute() {
        final int BATCH_SIZE = 500;
        Long lastId = 0L;

        int successCount = 0;
        int failCount = 0;
        int totalTargetCount = 0;

        LocalDate targetDate = LocalDate.now().minusDays(30);
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.plusDays(1).atStartOfDay();

        while (true) {
            List<User> users = userQueryService.findPurgeTargets(startDateTime, endDateTime, lastId, BATCH_SIZE);

            if (users.isEmpty()) {
                break;
            }

            for (User user : users) {
                lastId = user.getId();
                totalTargetCount++;

                try {
                    userCommandService.purgeUser(user);
                    successCount++;
                    log.debug("탈퇴 회원 개인정보 파기 완료: userId={}", user.getId());
                } catch (Exception e) {
                    failCount++;
                    log.error("탈퇴 회원 개인정보 파기 실패: userId={}", user.getId(), e);
                }
            }
        }
        log.info(
                "탈퇴 회원 개인정보 파기 작업 완료: target={}, completed={}, failed={}",
                totalTargetCount,
                successCount,
                failCount
        );
    }
}
