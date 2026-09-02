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

    @Scheduled(fixedDelay = 5_000L)
    @SchedulerLock(name = "auctionCloseScheduler")
    public void closeExpiredAuctions() {
        log.debug("경매 마감 스케줄러 실행");
        auctionCloseJob.closeExpiredAuctions();
    }
}
