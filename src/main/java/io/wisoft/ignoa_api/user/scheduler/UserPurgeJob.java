package io.wisoft.ignoa_api.user.scheduler;

import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserFacade;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeJob {

    private static final int BATCH_SIZE = 500;

    private final UserQueryService userQueryService;
    private final UserFacade userFacade;

    public void execute() {
        Long lastId = 0L;

        int successCount = 0;
        int failCount = 0;
        int totalTargetCount = 0;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        while (true) {
            List<User> users = userQueryService.findPurgeTargets(cutoff, lastId, BATCH_SIZE);

            if (users.isEmpty()) {
                break;
            }

            for (User user : users) {
                lastId = user.getId();
                totalTargetCount++;

                try {
                    userFacade.purgeUser(user);
                    successCount++;
                    log.debug("탈퇴 회원 개인정보 파기 완료: userId={}", user.getId());

                } catch (Exception e) {
                    failCount++;
                    log.error("탈퇴 회원 개인정보 파기 실패: userId={}", user.getId(), e);
                }
            }
        }

        if (totalTargetCount > 0) {
            log.info(
                    "탈퇴 회원 개인정보 파기 작업 완료: target={}, completed={}, failed={}",
                    totalTargetCount,
                    successCount,
                    failCount
            );
        }
    }
}
