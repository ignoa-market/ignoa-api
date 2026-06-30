package io.wisoft.ignoa_api.auction.listener;

import io.wisoft.ignoa_api.auction.service.AuctionCloseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpiredListener implements MessageListener {

    private static final String AUCTION_KEY_PREFIX = "auction:";
    private final AuctionCloseFacade auctionCloseFacade;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String expiredKey = message.toString();

            if (!expiredKey.startsWith(AUCTION_KEY_PREFIX)) {
                return;
            }

            long itemId = Long.parseLong(expiredKey.substring(AUCTION_KEY_PREFIX.length()));
            auctionCloseFacade.closeAuction(itemId);
        } catch (Exception e) {
            log.error("경매 마감 처리 실패 expiredKey={}", message, e);
        }
    }
}
