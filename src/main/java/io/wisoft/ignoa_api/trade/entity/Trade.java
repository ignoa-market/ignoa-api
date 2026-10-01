package io.wisoft.ignoa_api.trade.entity;

import io.wisoft.ignoa_api.global.common.BaseEntity;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "trades",
        indexes = @Index(name = "idx_trades_status_payment_deadline", columnList = "status, payment_deadline"))
public class Trade extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    @Column(length = 64)
    private String confirmingOrderId;

    @Column(nullable = false)
    private LocalDateTime paymentDeadline;

    private LocalDateTime paidAt;

    public static Trade create(Item item, User buyer, TradeType type, Long amount, LocalDateTime paymentDeadline) {
        return new Trade(null, item, buyer, item.getSeller(), amount, type,
                TradeStatus.PAYMENT_PENDING, null, paymentDeadline, null);
    }

    public boolean isBuyer(Long userId) {
        return buyer.getId().equals(userId);
    }
}
