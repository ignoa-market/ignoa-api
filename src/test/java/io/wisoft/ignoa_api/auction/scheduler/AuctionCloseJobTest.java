package io.wisoft.ignoa_api.auction.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wisoft.ignoa_api.auction.metric.AuctionCloseMetrics;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuctionCloseJobTest {

    @Test
    void 마감_조건을_충족하지_않은_경매는_완료로_집계하지_않는다() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemRepository.findExpiredActiveItemIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L));

        job.closeExpiredAuctions();

        assertThat(registry.get("auction.close.items").tag("outcome", "completed").counter().count())
                .isZero();
        assertThat(registry.find("auction.close.items").tag("outcome", "skipped").counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(1.0);
    }

    @Test
    void 실제_마감과_생략과_실패를_각각_집계한다() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemRepository.findExpiredActiveItemIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        when(auctionFacade.closeAuction(1L)).thenReturn(true);
        when(auctionFacade.closeAuction(3L)).thenThrow(new IllegalStateException("마감 실패"));

        job.closeExpiredAuctions();

        assertThat(registry.get("auction.close.items").tag("outcome", "completed").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("auction.close.items").tag("outcome", "skipped").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("auction.close.items").tag("outcome", "failed").counter().count())
                .isEqualTo(1.0);
    }
}
