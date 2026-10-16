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

    public boolean closeExpiredAuctions() {
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

        return targetCount == BATCH_SIZE && counts.failed.get() == 0;
    }

    private void closeInParallel(List<ExpiredItem> expiredItems, CloseCounts counts) {
        List<CompletableFuture<Void>> futures = expiredItems.stream()
                .map(item -> CompletableFuture.runAsync(
                        () -> closeAuction(item, counts),
                        auctionCloseExecutor
                ))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void closeAuction(ExpiredItem item, CloseCounts counts) {
        Timer.Sample sample = auctionCloseMetrics.start();

        try {
            if (auctionFacade.closeAuction(item.id())) {
                counts.completed.incrementAndGet();
                auctionCloseMetrics.recordLag(item.endAt(), LocalDateTime.now());
            } else {
                counts.skipped.incrementAndGet();
            }

        } catch (Exception e) {
            counts.failed.incrementAndGet();
            log.error("만료 경매 마감 처리 실패: itemId={}", item.id(), e);
        } finally {
            auctionCloseMetrics.recordItemClose(sample);
        }
    }

    private void recordResult(Timer.Sample sample, int targetCount, CloseCounts counts) {
        if (targetCount == 0) {
            return;
        }

        int completed = counts.completed.get();
        int skipped = counts.skipped.get();
        int failed = counts.failed.get();

        long durationNanos = auctionCloseMetrics.recordJob(sample, completed, skipped, failed);

        log.info(
                "경매 자동 마감 작업 완료: target={}, completed={}, skipped={}, failed={}, durationMs={}",
                targetCount,
                completed,
                skipped,
                failed,
                TimeUnit.NANOSECONDS.toMillis(durationNanos)
        );
    }

    private static final class CloseCounts {
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
    }
}
