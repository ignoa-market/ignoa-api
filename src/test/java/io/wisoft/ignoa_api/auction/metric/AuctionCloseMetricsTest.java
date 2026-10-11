package io.wisoft.ignoa_api.auction.metric;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionCloseMetricsTest {

    @Test
    void 마감_지연은_종료_시각부터_마감_완료_시각까지의_차이로_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseMetrics metrics = new AuctionCloseMetrics(registry);
        LocalDateTime endAt = LocalDateTime.of(2026, 10, 11, 10, 0, 3);

        metrics.recordLag(endAt, endAt.plusSeconds(17));

        Timer closeLag = registry.get("auction.close.lag").timer();
        assertThat(closeLag.count()).isEqualTo(1);
        assertThat(closeLag.totalTime(TimeUnit.SECONDS)).isEqualTo(17.0);
    }
}
