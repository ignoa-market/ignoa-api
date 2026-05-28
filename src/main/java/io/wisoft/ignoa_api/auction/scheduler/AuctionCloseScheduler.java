package io.wisoft.ignoa_api.auction.scheduler;


import io.wisoft.ignoa_api.auction.service.AuctionCloseJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseJob auctionCloseProcessor;

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "auctionCloseScheduler")
    public void closeExpiredBids() {
        log.info("경매 마감 스케줄러 실행");
        auctionCloseProcessor.closeExpiredBids();
    }
}
