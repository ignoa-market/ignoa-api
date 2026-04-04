package io.wisoft.ignoa_api.auction.listener;

import io.wisoft.ignoa_api.auction.service.AuctionCloseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpiredListener implements MessageListener {

    private final AuctionCloseManager auctionCloseManager;
    private static final String AUCTION_KEY_PREFIX = "auction:";

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String expiredKey = message.toString();

            if (!expiredKey.startsWith(AUCTION_KEY_PREFIX)) {
                return;
            }

            long itemId = Long.parseLong(expiredKey.substring(AUCTION_KEY_PREFIX.length()));
            auctionCloseManager.closeAuction(itemId);
        } catch (Exception e) {
            log.error("경매 마감 처리 실패 expiredKey={}", message.toString(), e);
        }
    }
}
