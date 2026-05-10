package io.wisoft.ignoa_api.user.scheduler;

import io.wisoft.ignoa_api.user.facade.UserFacade;
import io.wisoft.ignoa_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCleanupScheduler {

    private final UserService userService;
    private final UserFacade userFacade;

    @Scheduled(cron = "0 0 0 * * *")
    public void purgeExpiredWithdrawals() {
        log.info("탈퇴 회원 개인정보 파기 스케줄러 실행");
        userService.purgeExpiredWithdrawals();
        userFacade.purgeExpiredWithdrawals();
    }
}
