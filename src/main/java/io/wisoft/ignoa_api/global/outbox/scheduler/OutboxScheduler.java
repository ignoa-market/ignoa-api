package io.wisoft.ignoa_api.global.outbox.scheduler;

import io.wisoft.ignoa_api.global.outbox.service.OutboxWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxWorker outboxWorker;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("탈퇴 회원 프로필 사진 파기 스케줄러 실행");
        outboxWorker.execute();
    }
}
