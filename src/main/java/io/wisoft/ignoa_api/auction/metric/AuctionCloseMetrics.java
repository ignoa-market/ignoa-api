package io.wisoft.ignoa_api.auction.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class AuctionCloseMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer jobDuration;
    private final Timer closeLag;
    private final Counter completedItems;
    private final Counter skippedItems;
    private final Counter failedItems;

    public AuctionCloseMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.jobDuration = Timer.builder("auction.close.job.duration")
                .description("경매 자동 마감 Job 1회 실행 시간 (만료 경매 조회부터 모든 상품 마감까지)")
                .register(meterRegistry);

        this.closeLag = Timer.builder("auction.close.lag")
                .description("경매 종료 시각부터 마감 완료까지 걸린 시간 (상품 1건 기준)")
                .register(meterRegistry);

        this.completedItems = Counter.builder("auction.close.items")
                .description("경매 자동 마감 상품 처리 건수 (outcome별: completed, skipped, failed)")
                .tag("outcome", "completed")
                .register(meterRegistry);

        this.skippedItems = Counter.builder("auction.close.items")
                .description("경매 자동 마감 상품 처리 건수 (outcome별: completed, skipped, failed)")
                .tag("outcome", "skipped")
                .register(meterRegistry);

        this.failedItems = Counter.builder("auction.close.items")
                .description("경매 자동 마감 상품 처리 건수 (outcome별: completed, skipped, failed)")
                .tag("outcome", "failed")
                .register(meterRegistry);
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public long recordJob(Timer.Sample sample, int completedCount, int skippedCount, int failedCount) {
        completedItems.increment(completedCount);
        skippedItems.increment(skippedCount);
        failedItems.increment(failedCount);

        return sample.stop(jobDuration);
    }

    public void recordLag(LocalDateTime endAt, LocalDateTime closedAt) {
        closeLag.record(Duration.between(endAt, closedAt));
    }
}
