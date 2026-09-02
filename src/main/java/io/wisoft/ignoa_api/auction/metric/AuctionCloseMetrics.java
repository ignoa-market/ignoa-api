package io.wisoft.ignoa_api.auction.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AuctionCloseMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer jobDuration;
    private final Counter completedItems;
    private final Counter failedItems;

    public AuctionCloseMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.jobDuration = Timer.builder("auction.close.job.duration")
                .description("경매 자동 마감 Job 전체 처리 시간")
                .register(meterRegistry);

        this.completedItems = Counter.builder("auction.close.items")
                .description("경매 자동 마감 상품 처리 건수")
                .tag("outcome", "completed")
                .register(meterRegistry);

        this.failedItems = Counter.builder("auction.close.items")
                .description("경매 자동 마감 상품 처리 건수")
                .tag("outcome", "failed")
                .register(meterRegistry);
    }

    public Timer.Sample start() {
        return Timer.start(meterRegistry);
    }

    public long record(Timer.Sample sample, int completedCount, int failedCount) {
        completedItems.increment(completedCount);
        failedItems.increment(failedCount);

        return sample.stop(jobDuration);
    }
}
