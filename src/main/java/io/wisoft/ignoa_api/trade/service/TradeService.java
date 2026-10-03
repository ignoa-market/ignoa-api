package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.trade.dto.response.MyTradeResponse;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    private static final Duration AUCTION_PAYMENT_DEADLINE = Duration.ofHours(24);
    private static final Duration BUY_NOW_PAYMENT_DEADLINE = Duration.ofMinutes(30);

    private final ItemReader itemReader;
    private final TradeRepository tradeRepository;

    @Transactional
    public Trade createAuctionTrade(Long itemId) {
        Item item = itemReader.getById(itemId);

        Trade trade = Trade.create(
                item,
                item.getHighestBidder(),
                TradeType.AUCTION,
                item.getCurrentPrice(),
                LocalDateTime.now().plus(AUCTION_PAYMENT_DEADLINE)
        );

        Trade saved = tradeRepository.save(trade);
        log.info("거래 생성: tradeId={}, itemId={}, buyerId={}, type={}, amount={}, paymentDeadline={}",
                saved.getId(), itemId, saved.getBuyer().getId(), saved.getType(), saved.getAmount(), saved.getPaymentDeadline());
        return saved;
    }

    @Transactional
    public Trade createBuyNowTrade(Item item, User buyer) {
        Trade trade = Trade.create(
                item,
                buyer,
                TradeType.BUY_NOW,
                item.getBuyNowPrice(),
                LocalDateTime.now().plus(BUY_NOW_PAYMENT_DEADLINE)
        );

        Trade saved = tradeRepository.save(trade);
        log.info("거래 생성: tradeId={}, itemId={}, buyerId={}, type={}, amount={}, paymentDeadline={}",
                saved.getId(), item.getId(), saved.getBuyer().getId(), saved.getType(), saved.getAmount(), saved.getPaymentDeadline());
        return saved;
    }

    @Transactional
    public void cancelExpiredTrades(LocalDateTime now) {
        int canceled = tradeRepository.cancelExpiredIfPending(now);

        if (canceled > 0) {
            log.info("결제 기한 만료 거래 취소 완료: canceled={}", canceled);
        }
    }

    public MyTradeResponse getMyTrade(Long itemId, Long userId) {
        Trade trade = tradeRepository.findRecentTradeOfBuyer(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        return MyTradeResponse.from(trade);
    }
}
