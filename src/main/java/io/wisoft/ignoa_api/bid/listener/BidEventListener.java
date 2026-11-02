package io.wisoft.ignoa_api.bid.listener;

import io.wisoft.ignoa_api.bid.dto.response.BidBroadcast;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidPlaced(BidPlaceEvent event) {
        BidBroadcast broadcast = new BidBroadcast(
                event.itemId(),
                event.bidId(),
                event.currentPrice(),
                event.bidderNickname(),
                event.createdAt()
        );

        try {
            messagingTemplate.convertAndSend("/topic/items/" + event.itemId(), broadcast);
        } catch (RuntimeException e) {
            log.warn("입찰가 전송 실패: itemId={}, bidId={}, errorType={}, reason=입찰은 반영됐고 재조회 시 복구",
                    event.itemId(), event.bidId(), e.getClass().getSimpleName());
        }
    }
}
