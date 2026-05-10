package io.wisoft.ignoa_api.user.scheduler;

import io.wisoft.ignoa_api.global.infra.util.RedisLockUtils;
import io.wisoft.ignoa_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCleanupScheduler {

    private final UserService userService;
    private final RedisLockUtils redisLockUtils;

    @Scheduled(cron = "0 0 0 * * *")
    public void purgeExpiredWithdrawals() {
        final String lockName = "UserCleanupScheduler-purgeExpiredWithdrawals";
        if (redisLockUtils.acquireLock(
                lockName,
                "",
                Duration.ofSeconds(3L))
        ) {
            try {
                log.info("탈퇴 회원 개인정보 파기 실행");
                userService.purgeExpiredWithdrawals();
            } finally {
                redisLockUtils.release(lockName);
            }
        } else {
            log.info("탈퇴 회원 개인정보 파기 생략 (다른 서버에서 진행)");
        }
    }
}