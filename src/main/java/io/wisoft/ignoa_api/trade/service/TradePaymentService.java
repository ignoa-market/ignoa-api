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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
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

        log.info("결제 승인 시작: tradeId={}, orderId={}, type={}, amount={}",
                tradeId, orderId, trade.getType(), trade.getAmount());
    }

    @Transactional
    public void cancelConfirm(Long tradeId, String orderId) {
        Trade trade = tradeReader.getById(tradeId);

        if (tradeRepository.failConfirmIfConfirming(tradeId, orderId, TradeStatus.PAYMENT_PENDING) == 0) {
            log.debug("결제 승인 시작 되돌리기 생략: tradeId={}, orderId={}, reason=승인 중 아님", tradeId, orderId);
            return;
        }

        if (trade.isBuyNow()) {
            itemBuyNowService.cancel(trade.getItem().getId());
        }

        log.info("결제 승인 시작 되돌림: tradeId={}, orderId={}, reason=결제 서버 거절", tradeId, orderId);
    }

    @Transactional
    public boolean applyPaymentResult(Long tradeId, PaymentResult result) {
        Trade trade = tradeReader.getById(tradeId);

        return switch (result.status()) {
            case "DONE" -> {
                boolean paid = tradeRepository.markPaidIfConfirming(tradeId, result.orderId(), result.approvedAt()) == 1;

                if (paid) {
                    if (trade.isBuyNow()) {
                        itemBuyNowService.complete(trade.getItem().getId(), trade.getBuyer());
                    }
                    log.info("결제 완료 반영: tradeId={}, orderId={}, type={}, amount={}",
                            tradeId, result.orderId(), trade.getType(), result.amount());
                } else if (trade.getStatus() != TradeStatus.PAID) {
                    log.error("결제 완료 미반영: tradeId={}, orderId={}, tradeStatus={}, confirmingOrderId={}, action=수동 확인",
                            tradeId, result.orderId(), trade.getStatus(), trade.getConfirmingOrderId());
                } else {
                    log.debug("결제 완료 중복 수신 무시: tradeId={}, orderId={}", tradeId, result.orderId());
                }

                yield paid;
            }

            case "FAILED" -> {
                TradeStatus nextStatus = trade.getType() == TradeType.AUCTION
                        ? TradeStatus.PAYMENT_PENDING
                        : TradeStatus.CANCELED;

                boolean failed = tradeRepository.failConfirmIfConfirming(tradeId, result.orderId(), nextStatus) == 1;

                if (failed) {
                    if (trade.isBuyNow()) {
                        itemBuyNowService.cancel(trade.getItem().getId());
                    }
                    log.info("결제 실패 반영: tradeId={}, orderId={}, type={}, nextStatus={}, failureCode={}, failureMessage={}",
                            tradeId, result.orderId(), trade.getType(), nextStatus,
                            result.failureCode(), result.failureMessage());
                } else {
                    log.debug("결제 실패 결과 무시: tradeId={}, orderId={}, tradeStatus={}, reason=승인 중인 시도가 아님",
                            tradeId, result.orderId(), trade.getStatus());
                }

                yield failed;
            }

            default -> {
                log.debug("결제 결과 미확정, 콜백 대기: tradeId={}, orderId={}, status={}",
                        tradeId, result.orderId(), result.status());
                yield false;
            }
        };
    }
}
