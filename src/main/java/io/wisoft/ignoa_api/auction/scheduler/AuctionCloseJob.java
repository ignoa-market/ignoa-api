package io.wisoft.ignoa_api.auction.scheduler;

import io.micrometer.core.instrument.Timer;
import io.wisoft.ignoa_api.auction.metric.AuctionCloseMetrics;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.item.repository.ExpiredItem;
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
        CloseCounts counts = new CloseCounts();
        int targetCount = 0;

        try {
            List<ExpiredItem> expiredItems = itemRepository.findExpiredActiveItems(
                    LocalDateTime.now(),
                    PageRequest.of(0, BATCH_SIZE)
            );
            targetCount = expiredItems.size();

            // Worker Pool(4) - 비동기 병렬
            closeInParallel(expiredItems, counts);

        } finally {
            recordResult(sample, targetCount, counts);
        }
    }

    private void closeInParallel(List<ExpiredItem> expiredItems, CloseCounts counts) {
        List<CompletableFuture<Void>> futures = expiredItems.stream()
                .map(item -> CompletableFuture.runAsync(
                        () -> closeAuction(item.id(), counts.completed, counts.skipped, counts.failed),
                        auctionCloseExecutor
                ))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void closeAuction(Long itemId, AtomicInteger completedCount, AtomicInteger skippedCount, AtomicInteger failedCount) {
        try {
            if (auctionFacade.closeAuction(itemId)) completedCount.incrementAndGet();
            else skippedCount.incrementAndGet();

        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("만료 경매 마감 처리 실패: itemId={}", itemId, e);
        }
    }

    private void recordResult(Timer.Sample sample, int targetCount, CloseCounts counts) {
        int completed = counts.completed.get();
        int skipped = counts.skipped.get();
        int failed = counts.failed.get();

        long durationNanos = auctionCloseMetrics.record(sample, completed, skipped, failed);

        if (targetCount > 0) {
            log.info(
                    "경매 자동 마감 작업 완료: target={}, completed={}, skipped={}, failed={}, durationMs={}",
                    targetCount,
                    completed,
                    skipped,
                    failed,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos)
            );
        }
    }

    private static final class CloseCounts {
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
    }
}
