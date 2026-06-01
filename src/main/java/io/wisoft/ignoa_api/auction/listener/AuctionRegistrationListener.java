package io.wisoft.ignoa_api.auction.listener;

import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.auction.service.AuctionRedisService;
import io.wisoft.ignoa_api.auction.event.AuctionCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionRegistrationListener {

    private final AuctionRedisService auctionRedisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuctionRegistered(AuctionRegisteredEvent event) {
        auctionRedisService.registerTtl(event.itemId(), event.endAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemDeleted(AuctionCanceledEvent event) {
        auctionRedisService.deleteTtl(event.itemId());
    }
}
