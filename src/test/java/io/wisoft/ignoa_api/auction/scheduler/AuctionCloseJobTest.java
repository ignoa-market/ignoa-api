package io.wisoft.ignoa_api.auction.scheduler;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wisoft.ignoa_api.auction.metric.AuctionCloseMetrics;
import io.wisoft.ignoa_api.auction.service.AuctionFacade;
import io.wisoft.ignoa_api.item.repository.dto.ExpiredItem;
import io.wisoft.ignoa_api.item.repository.ItemQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuctionCloseJobTest {

    @Test
    void 마감_조건을_충족하지_않은_경매는_완료로_집계하지_않는다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredItem(1L)));

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
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredItem(1L), expiredItem(2L), expiredItem(3L)));
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

    @Test
    void 마감_지연은_실제로_마감한_경매만_종료_시각_기준으로_기록한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );
        LocalDateTime endAt = LocalDateTime.now().minusSeconds(10);

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(
                        new ExpiredItem(1L, endAt),
                        new ExpiredItem(2L, endAt),
                        new ExpiredItem(3L, endAt)
                ));
        when(auctionFacade.closeAuction(1L)).thenReturn(true);
        when(auctionFacade.closeAuction(3L)).thenThrow(new IllegalStateException("마감 실패"));

        job.closeExpiredAuctions();

        Timer closeLag = registry.get("auction.close.lag").timer();
        assertThat(closeLag.count()).isEqualTo(1);
        assertThat(closeLag.totalTime(TimeUnit.SECONDS)).isBetween(10.0, 11.0);
    }

    @Test
    void 마감_1건_처리_시간은_완료_생략_실패한_경매마다_기록한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredItem(1L), expiredItem(2L), expiredItem(3L)));
        when(auctionFacade.closeAuction(1L)).thenReturn(true);
        when(auctionFacade.closeAuction(3L)).thenThrow(new IllegalStateException("마감 실패"));

        job.closeExpiredAuctions();

        assertThat(registry.get("auction.close.item.duration").timer().count()).isEqualTo(3);
    }

    @Test
    void 배치_1회_처리_시간은_처리할_경매가_있는_실행만_기록한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(registry),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(expiredItem(1L)));
        when(auctionFacade.closeAuction(1L)).thenReturn(true);

        job.closeExpiredAuctions();
        job.closeExpiredAuctions();

        assertThat(registry.get("auction.close.job.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void 묶음_1000건을_실패_없이_처리하면_이어서_처리하도록_true를_반환한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(new SimpleMeterRegistry()),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(expiredItems(1_000));
        when(auctionFacade.closeAuction(any())).thenReturn(true);

        assertThat(job.closeExpiredAuctions()).isTrue();
    }

    @Test
    void 묶음이_1000건보다_적으면_남은_경매가_없으므로_false를_반환한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(new SimpleMeterRegistry()),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(expiredItems(999));
        when(auctionFacade.closeAuction(any())).thenReturn(true);

        assertThat(job.closeExpiredAuctions()).isFalse();
    }

    @Test
    void 묶음_1000건_중_실패가_있으면_같은_경매를_바로_다시_조회하지_않도록_false를_반환한다() {
        ItemQueryRepository itemQueryRepository = mock(ItemQueryRepository.class);
        AuctionFacade auctionFacade = mock(AuctionFacade.class);
        AuctionCloseJob job = new AuctionCloseJob(
                auctionFacade,
                itemQueryRepository,
                new AuctionCloseMetrics(new SimpleMeterRegistry()),
                Runnable::run
        );

        when(itemQueryRepository.findExpiredActiveItems(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(expiredItems(1_000));
        when(auctionFacade.closeAuction(any())).thenReturn(true);
        when(auctionFacade.closeAuction(1L)).thenThrow(new IllegalStateException("마감 실패"));

        assertThat(job.closeExpiredAuctions()).isFalse();
    }

    private static ExpiredItem expiredItem(Long id) {
        return new ExpiredItem(id, LocalDateTime.now());
    }

    private static List<ExpiredItem> expiredItems(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(AuctionCloseJobTest::expiredItem)
                .toList();
    }
}
