package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.service.ItemBuyNowService;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentPrepareRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradePaymentService {

    private final ItemBuyNowService itemBuyNowService;
    private final TradeReader tradeReader;

    private final TradeRepository tradeRepository;

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

        if (trade.isBuyNow() && !trade.getItem().isActive()) {
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }

        return new PaymentPrepareRequest(
                tradeId, trade.getAmount(), trade.getItem().getTitle()
        );
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

        if (trade.isBuyNow()) {
            itemBuyNowService.reserve(trade.getItem().getId(), trade.getAmount(), now);
        }
    }

    @Transactional
    public void cancelConfirm(Long tradeId, String orderId) {
        Trade trade = tradeReader.getById(tradeId);

        if (tradeRepository.failConfirmIfConfirming(tradeId, orderId, TradeStatus.PAYMENT_PENDING) == 1
                && trade.isBuyNow()) {
            itemBuyNowService.cancel(trade.getItem().getId());
        }
    }

    @Transactional
    public boolean applyPaymentResult(Long tradeId, PaymentResult result) {
        Trade trade = tradeReader.getById(tradeId);

        return switch (result.status()) {
            case "DONE" -> {
                boolean paid = tradeRepository.markPaidIfConfirming(tradeId, result.orderId(), result.approvedAt()) == 1;

                if (paid && trade.isBuyNow()) {
                    itemBuyNowService.complete(trade.getItem().getId(), trade.getBuyer());
                }

                yield paid;
            }

            case "FAILED" -> {
                TradeStatus nextStatus = trade.getType() == TradeType.AUCTION
                        ? TradeStatus.PAYMENT_PENDING
                        : TradeStatus.CANCELED;

                boolean failed = tradeRepository.failConfirmIfConfirming(tradeId, result.orderId(), nextStatus) == 1;

                if (failed && trade.isBuyNow()) {
                    itemBuyNowService.cancel(trade.getItem().getId());
                }

                yield failed;
            }

            default -> false;
        };
    }
}
