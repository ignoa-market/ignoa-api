package io.wisoft.ignoa_api.auction.scheduler;


import io.wisoft.ignoa_api.auction.service.AuctionCloseProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

    private final AuctionCloseProcessor auctionCloseProcessor;

    @Scheduled(cron = "0 */5 * * * *")
    public void closeExpiredBids() {
        log.info("경매 마감 스케줄러 실행");
        auctionCloseProcessor.closeExpiredBids();
    }
}
