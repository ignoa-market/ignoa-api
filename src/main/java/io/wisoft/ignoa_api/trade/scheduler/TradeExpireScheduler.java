package io.wisoft.ignoa_api.trade.scheduler;

import io.wisoft.ignoa_api.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExpireScheduler {

    private final TradeService tradeService;

    @Scheduled(fixedDelay = 60_000L)
    @SchedulerLock(name = "tradeExpireScheduler")
    public void cancelExpiredTrades() {
        log.debug("결제 기한 만료 거래 취소 스케줄러 실행");
        tradeService.cancelExpiredTrades(LocalDateTime.now());
    }
}
