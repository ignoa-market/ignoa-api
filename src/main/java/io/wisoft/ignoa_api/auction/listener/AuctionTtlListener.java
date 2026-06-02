package io.wisoft.ignoa_api.auction.listener;

import io.wisoft.ignoa_api.auction.event.AuctionClosedEvent;
import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.auction.service.AuctionTtlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionTtlListener {

    private final AuctionTtlService auctionRedisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionRegistered(AuctionRegisteredEvent event) {
        auctionRedisService.registerTtl(event.itemId(), event.endAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionClosed(AuctionClosedEvent event) {
        auctionRedisService.deleteTtl(event.itemId());
    }
}
