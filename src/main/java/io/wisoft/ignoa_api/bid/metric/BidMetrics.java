package io.wisoft.ignoa_api.bid.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BidMetrics {

    private final Counter acceptedBids;
    private final Counter priceConflictBids;

    public BidMetrics(MeterRegistry meterRegistry) {
        this.acceptedBids = Counter.builder("bid.place")
                .description("입찰 요청 결과 건수 (outcome별: accepted, price_conflict)")
                .tag("outcome", "accepted")
                .register(meterRegistry);

        this.priceConflictBids = Counter.builder("bid.place")
                .description("입찰 요청 결과 건수 (outcome별: accepted, price_conflict)")
                .tag("outcome", "price_conflict")
                .register(meterRegistry);
    }

    public void countAccepted() {
        acceptedBids.increment();
    }

    public void countPriceConflict() {
        priceConflictBids.increment();
    }
}
