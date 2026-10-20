package io.wisoft.ignoa_api.auction.metric;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.wisoft.ignoa_api.item.repository.ItemQueryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuctionBacklogMetrics implements MeterBinder {

    private final ItemQueryRepository itemQueryRepository;

    public AuctionBacklogMetrics(ItemQueryRepository itemQueryRepository) {
        this.itemQueryRepository = itemQueryRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("auction.close.backlog", this::countOverdue)
                .description("종료 시각이 지났지만 아직 마감되지 않은 경매 수 (수집 시점 기준)")
                .register(registry);
    }

    private long countOverdue() {
        return itemQueryRepository.countExpiredActiveItems(LocalDateTime.now());
    }
}
