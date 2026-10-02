package io.wisoft.ignoa_api.trade.repository;

import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    // 승인 시작: PAYMENT_PENDING → CONFIRMING
    @Modifying
    @Query("""
            UPDATE Trade t
            SET t.status = 'CONFIRMING',
                t.confirmingOrderId = :orderId
            WHERE t.id = :id
                AND t.status = 'PAYMENT_PENDING'
                AND t.paymentDeadline > :now
            """)
    int startConfirmIfPending(@Param("id") Long id, @Param("orderId") String orderId,
                              @Param("now") LocalDateTime now);

    // 결제 성공: CONFIRMING → PAID
    @Modifying
    @Query("""
            UPDATE Trade t
            SET t.status = 'PAID',
                t.paidAt = :paidAt
            WHERE t.id = :id
                AND t.status = 'CONFIRMING'
                AND t.confirmingOrderId = :orderId
            """)
    int markPaidIfConfirming(@Param("id") Long id, @Param("orderId") String orderId,
                             @Param("paidAt") LocalDateTime paidAt);

    // 결제 실패: CONFIRMING → PAYMENT_PENDING(낙찰·거절) 또는 CANCELED(즉시구매)
    @Modifying
    @Query("""
            UPDATE Trade t
            SET t.status = :nextStatus
            WHERE t.id = :id
                AND t.status = 'CONFIRMING'
                AND t.confirmingOrderId = :orderId
            """)
    int failConfirmIfConfirming(@Param("id") Long id, @Param("orderId") String orderId,
                                @Param("nextStatus") TradeStatus nextStatus);

    // 결제 기한 만료: PAYMENT_PENDING → CANCELED (CONFIRMING은 건드리지 않음)
    @Modifying
    @Query("""
            UPDATE Trade t
            SET t.status = 'CANCELED'
            WHERE t.status = 'PAYMENT_PENDING'
                AND t.paymentDeadline <= :now
            """)
    int cancelExpiredIfPending(@Param("now") LocalDateTime now);
}
