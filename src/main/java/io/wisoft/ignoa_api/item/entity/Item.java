package io.wisoft.ignoa_api.item.entity;

import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "items")
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    private ItemCondition itemCondition;

    @Column(nullable = false)
    private Long startPrice;

    @Column(nullable = false)
    private Long currentPrice;

    @Column
    private Long buyNowPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(nullable = false)
    private LocalDateTime endAt;

    public static Item create(User seller, String title, String description, String category,
                              ItemCondition itemCondition, Long startPrice, Long buyNowPrice,
                              LocalDateTime endAt) {
        return new Item(
                null,
                seller,
                null,
                title,
                description,
                category,
                itemCondition,
                startPrice,
                startPrice,
                buyNowPrice,
                ItemStatus.ACTIVE,
                endAt
        );
    }

    public void updateInfo(String title, String description, String category, LocalDateTime endAt) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (endAt != null) this.endAt = endAt;
    }

    public void raisePriceTo(Long newPrice) {
        this.currentPrice = newPrice;
    }

    public boolean isActive() {
        return this.status == ItemStatus.ACTIVE
                && this.endAt.isAfter(LocalDateTime.now());
    }

    public boolean isSeller(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public boolean isValidBidPrice(Long bidPrice) {
        return this.currentPrice < bidPrice;
    }

    public void closeAsNoBid() {
        this.status = ItemStatus.NO_BID_CLOSED;
    }

    public void closeWithWinner(User winner) {
        this.winner = winner;
        this.status = ItemStatus.CLOSED;
    }

    public boolean isClosed() {
        return this.status != ItemStatus.ACTIVE;
    }
}
