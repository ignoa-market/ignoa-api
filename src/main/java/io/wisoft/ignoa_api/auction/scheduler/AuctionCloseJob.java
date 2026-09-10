package io.wisoft.ignoa_api.auction.scheduler;

import io.micrometer.core.instrument.Timer;
import io.wisoft.ignoa_api.auction.metric.AuctionCloseMetrics;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseJob {

    private static final int BATCH_SIZE = 1_000;

    private final AuctionFacade auctionFacade;
    private final ItemRepository itemRepository;
    private final AuctionCloseMetrics auctionCloseMetrics;
    private final Executor auctionCloseExecutor;

    public void closeExpiredAuctions() {
        Timer.Sample sample = auctionCloseMetrics.start();

        int selectedCount = 0;

        AtomicInteger completedCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        try {
            List<Long> expiredItemIds = itemRepository.findExpiredActiveItemIds(
                    LocalDateTime.now(),
                    PageRequest.of(0, BATCH_SIZE)
            );

            selectedCount = expiredItemIds.size();

            List<CompletableFuture<Void>> futures = expiredItemIds.stream()
                    .map(itemId -> CompletableFuture.runAsync(
                            () -> closeAuction(itemId, completedCount, failedCount),
                            auctionCloseExecutor
                    ))
                    .toList();

            CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            ).join();

        } finally {
            long durationNanos = auctionCloseMetrics.record(
                    sample,
                    completedCount.get(),
                    failedCount.get()
            );

            if (selectedCount > 0) {
                log.info(
                        "경매 자동 마감 작업 완료: target={}, completed={}, failed={}, durationMs={}",
                        selectedCount,
                        completedCount.get(),
                        failedCount.get(),
                        TimeUnit.NANOSECONDS.toMillis(durationNanos)
                );
            }
        }
    }

    private void closeAuction(
            Long itemId,
            AtomicInteger completedCount,
            AtomicInteger failedCount
    ) {
        try {
            auctionFacade.closeAuction(itemId);
            completedCount.incrementAndGet();
        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("만료 경매 마감 처리 실패: itemId={}", itemId, e);
        }
    }
}
