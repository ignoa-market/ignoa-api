package io.wisoft.ignoa_api.trade.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeResolveScheduler {

    private final TradeResolveJob tradeResolveJob;

    @Scheduled(fixedDelay = 300_000L)
    @SchedulerLock(name = "tradeResolveScheduler")
    public void run() {
        log.debug("결제 결과 미확정 거래 재확인 스케줄러 시작");
        tradeResolveJob.resolve();
    }
}
