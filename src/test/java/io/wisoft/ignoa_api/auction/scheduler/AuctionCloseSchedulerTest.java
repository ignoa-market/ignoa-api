package io.wisoft.ignoa_api.auction.scheduler;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionCloseSchedulerTest {

    @Test
    void 묶음을_실패_없이_가득_처리하면_다음_주기를_기다리지_않고_이어서_실행한다() {
        AuctionCloseJob auctionCloseJob = mock(AuctionCloseJob.class);
        AuctionCloseScheduler scheduler = new AuctionCloseScheduler(auctionCloseJob);

        when(auctionCloseJob.closeExpiredAuctions()).thenReturn(true, true, false);

        scheduler.closeExpiredAuctions();

        verify(auctionCloseJob, times(3)).closeExpiredAuctions();
    }

    @Test
    void 이어서_처리할_경매가_없으면_한_번만_실행한다() {
        AuctionCloseJob auctionCloseJob = mock(AuctionCloseJob.class);
        AuctionCloseScheduler scheduler = new AuctionCloseScheduler(auctionCloseJob);

        when(auctionCloseJob.closeExpiredAuctions()).thenReturn(false);

        scheduler.closeExpiredAuctions();

        verify(auctionCloseJob, times(1)).closeExpiredAuctions();
    }
}
