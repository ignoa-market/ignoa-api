package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
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
public class TradeService {

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

        return new PaymentPrepareRequest(tradeId, trade.getAmount(), trade.getItem().getTitle());
    }

    @Transactional
    public void startConfirm(Long tradeId, Long buyerId, String orderId) {
        Trade trade = tradeReader.getById(tradeId);

        if (!trade.isBuyer(buyerId)) {
            throw new BusinessException(ErrorCode.TRADE_ACCESS_DENIED);
        }

        if (tradeRepository.startConfirmIfPending(tradeId, orderId, LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.TRADE_NOT_PAYABLE);
        }
    }

    @Transactional
    public void cancelConfirm(Long tradeId, String orderId) {
        // 결제 서버가 Toss 호출 전에 거절 -> 다시 결제할 수 있게 되돌림
        tradeRepository.failConfirmIfConfirming(
                tradeId, orderId, TradeStatus.PAYMENT_PENDING
        );
    }

    @Transactional
    public boolean applyPaymentResult(Long tradeId, PaymentResult result) {
        return switch (result.status()) {
            case "DONE" -> tradeRepository.markPaidIfConfirming(tradeId, result.orderId(), result.approvedAt()) == 1;

            case "FAILED" -> {
                Trade trade = tradeReader.getById(tradeId);

                TradeStatus nextStatus = trade.getType() == TradeType.AUCTION
                        ? TradeStatus.PAYMENT_PENDING
                        : TradeStatus.CANCELLED;

                yield tradeRepository.failConfirmIfConfirming(tradeId, result.orderId(), nextStatus) == 1;
            }

            default -> false;
        };
    }
}
