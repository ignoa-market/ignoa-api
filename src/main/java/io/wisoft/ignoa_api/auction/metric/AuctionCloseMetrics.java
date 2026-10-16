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
    private final Timer itemDuration;
    private final Timer closeLag;
    private final Counter completedItems;
    private final Counter skippedItems;
    private final Counter failedItems;

    public AuctionCloseMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.jobDuration = Timer.builder("auction.close.job.duration")
                .description("배치 1회 처리 시간 (처리할 경매가 있는 실행만, 만료 경매 조회부터 최대 1,000건 마감까지)")
                .register(meterRegistry);

        this.itemDuration = Timer.builder("auction.close.item.duration")
                .description("마감 1건 처리 시간 (마감 트랜잭션만, 앞 상품을 기다린 시간 제외)")
                .register(meterRegistry);

        this.closeLag = Timer.builder("auction.close.lag")
                .description("마감 확정 시간 (경매 1건, 종료 시각부터 서버에서 마감이 확정되기까지, 폴링 대기 포함)")
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

    public void recordItemClose(Timer.Sample sample) {
        sample.stop(itemDuration);
    }
}
