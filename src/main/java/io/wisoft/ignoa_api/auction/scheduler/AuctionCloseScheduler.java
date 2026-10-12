package io.wisoft.ignoa_api.auction.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private static final Duration MAX_RUN_DURATION = Duration.ofMinutes(5);

    private final AuctionCloseJob auctionCloseJob;

    @Scheduled(fixedDelay = 1_000L)
    @SchedulerLock(name = "auctionCloseScheduler")
    public void closeExpiredAuctions() {
        log.debug("경매 자동 마감 스케줄러 시작");

        long startedAt = System.nanoTime();
        int run = 1;
        while (auctionCloseJob.closeExpiredAuctions()) {
            if (System.nanoTime() - startedAt >= MAX_RUN_DURATION.toNanos()) {
                log.info("경매 자동 마감 이어서 실행 중단: run={}, reason=실행 시간이 5분을 넘어 락이 풀리기 전에 멈추고 다음 주기에 이어서 처리", run);
                return;
            }
            run++;
            log.debug("경매 자동 마감 이어서 실행: run={}, reason=직전 묶음 1,000건을 실패 없이 모두 처리", run);
        }
    }
}
