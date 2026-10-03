package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.item.service.ItemBuyNowService;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentResultApplier {

    private final TradeReader tradeReader;
    private final TradeRepository tradeRepository;

    private final ItemBuyNowService itemBuyNowService;

    public boolean apply(Long tradeId, PaymentResult result) {
        if (result.status() == null) {
            log.warn("결제 결과 무시: tradeId={}, orderId={}, reason=상태 누락", tradeId, result.orderId());
            return false;
        }

        Trade trade = tradeReader.getById(tradeId);

        return switch (result.status()) {
            case "DONE" -> applyDone(trade, result);
            case "FAILED" -> applyFailed(trade, result);
            default -> {
                log.debug("결제 결과 대기: tradeId={}, orderId={}, status={}, reason=결과 미확정",
                        tradeId, result.orderId(), result.status());
                yield false;
            }
        };
    }

    private boolean applyDone(Trade trade, PaymentResult result) {
        int paidRow = tradeRepository.markPaidIfConfirming(trade.getId(), result.orderId(), result.approvedAt());

        if (paidRow == 0) {
            log.debug("결제 완료 무시: tradeId={}, orderId={}, status={}, currentOrderId={}, reason=이미 처리됐거나 이전 시도의 결과",
                    trade.getId(), result.orderId(), trade.getStatus(), trade.getConfirmingOrderId());
            return false;
        }

        if (trade.isBuyNow()) {
            itemBuyNowService.complete(trade.getItem().getId(), trade.getBuyer());
        }

        log.info("결제 완료 반영: tradeId={}, orderId={}, type={}, amount={}", trade.getId(), result.orderId(), trade.getType(), result.amount());
        return true;
    }

    private boolean applyFailed(Trade trade, PaymentResult result) {
        TradeStatus nextStatus = trade.isBuyNow()
                ? TradeStatus.CANCELED
                : TradeStatus.PAYMENT_PENDING;

        int failedRow = tradeRepository.failConfirmIfConfirming(trade.getId(), result.orderId(), nextStatus);

        if (failedRow == 0) {
            log.debug("결제 실패 무시: tradeId={}, orderId={}, status={}, currentOrderId={}, reason=이미 처리됐거나 이전 시도의 결과",
                    trade.getId(), result.orderId(), trade.getStatus(), trade.getConfirmingOrderId());
            return false;
        }

        if (trade.isBuyNow()) {
            itemBuyNowService.cancel(trade.getItem().getId());
        }

        log.info("결제 실패 반영: tradeId={}, orderId={}, type={}, nextStatus={}, failureCode={}, failureMessage={}",
                trade.getId(), result.orderId(), trade.getType(), nextStatus, result.failureCode(), result.failureMessage());

        return true;
    }
}
