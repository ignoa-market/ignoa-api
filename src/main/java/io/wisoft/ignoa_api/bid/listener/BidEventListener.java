package io.wisoft.ignoa_api.bid.listener;

import io.wisoft.ignoa_api.bid.dto.response.BidBroadcast;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BidEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidPlaced(BidPlaceEvent event) {
        BidBroadcast broadcast = new BidBroadcast(
                event.productId(),
                event.currentPrice(),
                event.bidderName(),
                event.createdAt()
        );

        messagingTemplate.convertAndSend("/topic/products/" + event.productId(), broadcast);
    }
}
