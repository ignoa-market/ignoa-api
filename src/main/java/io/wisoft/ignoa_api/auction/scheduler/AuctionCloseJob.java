package io.wisoft.ignoa_api.auction.scheduler;

import io.micrometer.core.instrument.Timer;
import io.wisoft.ignoa_api.auction.metric.AuctionCloseMetrics;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseJob {

    private final AuctionFacade auctionFacade;
    private final ItemRepository itemRepository;

    private final AuctionCloseMetrics auctionCloseMetrics;

    public void closeExpiredAuctions() {
        Timer.Sample sample = auctionCloseMetrics.start();

        int selectedCount = 0;
        int completedCount = 0;
        int failedCount = 0;

        try {
            List<Item> expiredItems = itemRepository.findExpiredActiveItems(LocalDateTime.now());

            selectedCount = expiredItems.size();

            for (Item item : expiredItems) {
                try {
                    auctionFacade.closeAuction(item.getId());
                    completedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("만료 경매 마감 처리 실패 itemId={}", item.getId(), e);
                }
            }
        } finally {
            long durationNanos = auctionCloseMetrics.record(
                    sample, completedCount, failedCount
            );

            if (selectedCount > 0) {
                log.info(
                        "경매 자동 마감 Job 완료: selected={}, completed={}, failed={}, durationMs={}",
                        selectedCount,
                        completedCount,
                        failedCount,
                        TimeUnit.NANOSECONDS.toMillis(durationNanos)
                );
            }
        }
    }
}
