package io.wisoft.ignoa_api.user.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeScheduler {

    private final UserPurgeJob userPurgeJob;

    @Scheduled(cron = "0 0 1 * * *")
    @SchedulerLock(name = "userPurgeScheduler")
    public void purgeExpiredWithdrawals() {
        log.info("탈퇴 회원 개인정보 파기 스케줄러 실행");
        userPurgeJob.execute();
    }
}
