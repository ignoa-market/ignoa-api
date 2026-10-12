package io.wisoft.ignoa_api.auction.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseJob auctionCloseJob;

    @Scheduled(fixedDelay = 1_000L)
    @SchedulerLock(name = "auctionCloseScheduler")
    public void closeExpiredAuctions() {
        log.debug("경매 자동 마감 스케줄러 시작");

        int run = 1;
        while (auctionCloseJob.closeExpiredAuctions()) {
            run++;
            log.debug("경매 자동 마감 이어서 실행: run={}, reason=직전 묶음 1,000건을 실패 없이 모두 처리", run);
        }
    }
}
