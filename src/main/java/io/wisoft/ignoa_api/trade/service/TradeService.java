package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.trade.dto.response.MyTradeResponse;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
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

    private final TradeReader tradeReader;
    private final ItemReader itemReader;

    private final TradeRepository tradeRepository;
    private final ItemRepository itemRepository;
    private final ChatRoomService chatRoomService;
    private final BidRepository bidRepository;

    public PaymentPrepareRequest validatePrepare(Long tradeId, Long buyerId) {
        Trade trade = tradeReader.getById(tradeId);

        if (!trade.isBuyer(buyerId)) {
            throw new BusinessException(ErrorCode.TRADE_ACCESS_DENIED);
        }

        if (trade.getStatus() != TradeStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.TRADE_NOT_PAYABLE);
        }

        if (!trade.getPaymentDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TRADE_PAYMENT_EXPIRED);
        }

        if (trade.getType() == TradeType.BUY_NOW && !trade.getItem().isActive()) {
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }

        return new PaymentPrepareRequest(tradeId, trade.getAmount(), trade.getItem().getTitle());
    }

    @Transactional
    public void startConfirm(Long tradeId, Long buyerId, String orderId) {
        Trade trade = tradeReader.getById(tradeId);

        if (!trade.isBuyer(buyerId)) {
            throw new BusinessException(ErrorCode.TRADE_ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();

        if (tradeRepository.startConfirmIfPending(tradeId, orderId, now) == 0) {
            throw new BusinessException(ErrorCode.TRADE_NOT_PAYABLE);
        }

        if (trade.getType() == TradeType.BUY_NOW) {
            startBuyNow(trade, now);
        }
    }

    // 즉시구매 결제 중 다른 즉시구매·경매 마감을 막는다. 실패하면 위의 trade 변경도 함께 롤백된다
    private void startBuyNow(Trade trade, LocalDateTime now) {
        int locked = itemRepository.lockForBuyNowIfActive(trade.getItem().getId(), trade.getAmount(), now);

        if (locked == 0) {
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }
    }

    // 결제 서버가 Toss 호출 전에 거절 -> 다시 결제할 수 있게 되돌림
    @Transactional
    public void cancelConfirm(Long tradeId, String orderId) {
        Trade trade = tradeReader.getById(tradeId);

        if (tradeRepository.failConfirmIfConfirming(tradeId, orderId, TradeStatus.PAYMENT_PENDING) == 1
                && trade.getType() == TradeType.BUY_NOW) {
            cancelBuyNow(trade);
        }
    }

    @Transactional
    public boolean applyPaymentResult(Long tradeId, PaymentResult result) {
        Trade trade = tradeReader.getById(tradeId);

        return switch (result.status()) {
            case "DONE" -> {
                boolean paid = tradeRepository.markPaidIfConfirming(tradeId, result.orderId(), result.approvedAt()) == 1;

                if (paid && trade.getType() == TradeType.BUY_NOW) {
                    completeBuyNow(trade);
                }

                yield paid;
            }

            case "FAILED" -> {
                TradeStatus nextStatus = trade.getType() == TradeType.AUCTION
                        ? TradeStatus.PAYMENT_PENDING
                        : TradeStatus.CANCELED;

                boolean failed = tradeRepository.failConfirmIfConfirming(tradeId, result.orderId(), nextStatus) == 1;

                if (failed && trade.getType() == TradeType.BUY_NOW) {
                    cancelBuyNow(trade);
                }

                yield failed;
            }

            default -> false;
        };
    }

    // 즉시구매 결제 완료: 상품 마감 → 나머지 입찰 패찰 → 채팅방 생성
    private void completeBuyNow(Trade trade) {
        Long itemId = trade.getItem().getId();

        itemRepository.closeBuyNowIfPending(itemId, trade.getBuyer());
        bidRepository.markLosingBids(itemId);
        chatRoomService.createChatRoom(itemId);
    }

    private void cancelBuyNow(Trade trade) {
        itemRepository.cancelBuyNowIfPending(trade.getItem().getId());
    }

    @Transactional
    public void cancelExpiredTrades(LocalDateTime now) {
        int canceled = tradeRepository.cancelExpiredIfPending(now);

        if (canceled > 0) {
            log.info("결제 기한 만료 거래 취소 완료: canceled={}", canceled);
        }
    }

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

        return tradeRepository.save(trade);
    }

    public MyTradeResponse getMyTrade(Long itemId, Long userId) {
        Trade trade = tradeRepository.findRecentTradeOfBuyer(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));

        return MyTradeResponse.from(trade);
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

        return tradeRepository.save(trade);
    }
}
