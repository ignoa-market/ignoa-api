package io.wisoft.ignoa_api.global.outbox.scheduler;

import io.wisoft.ignoa_api.global.outbox.service.OutboxWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxWorker outboxWorker;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "outboxScheduler")
    public void run() {
        log.debug("Outbox 처리 스케줄러 실행");
        outboxWorker.execute();
    }
}
