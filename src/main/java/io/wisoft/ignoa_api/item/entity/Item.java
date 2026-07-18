package io.wisoft.ignoa_api.item.entity;

import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Entity
@Getter
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "items",
        indexes = {
            @Index(name = "idx_items_status_created", columnList = "status, created_at"),
            @Index(name = "idx_items_status_end_at", columnList = "status, end_at")
        })
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "highest_bidder_id")
    private User highestBidder;

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

    @Column(nullable = false)
    private Long buyNowPrice;

    @Column(nullable = false)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Version
    private Long version;

    public static Item create(User seller, String title, String description, String category,
                              ItemCondition itemCondition, String brand, Long startPrice, Long buyNowPrice,
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
                brand,
                ItemStatus.ACTIVE,
                endAt,
                null
        );
    }

    public void update(String title, String description, String category, String brand,
                       ItemCondition itemCondition, Long buyNowPrice, LocalDateTime endAt) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (brand != null) this.brand = brand;
        if (itemCondition != null) this.itemCondition = itemCondition;
        if (buyNowPrice != null) this.buyNowPrice = buyNowPrice;
        if (endAt != null) this.endAt = endAt;
    }

    public boolean isSeller(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public boolean isActive() {
        return this.status == ItemStatus.ACTIVE
                && this.endAt.isAfter(LocalDateTime.now());
    }

    public boolean isClosed() {
        return this.status != ItemStatus.ACTIVE;
    }

    public boolean isSold() {
        return this.status == ItemStatus.BID_CLOSED
                || this.status == ItemStatus.BUY_NOW_CLOSED;
    }

    public boolean isValidBidPrice(Long bidPrice) {
        return this.currentPrice < bidPrice;
    }

    public boolean isValidBuyNowPrice(Long buyNowPrice) {
        return buyNowPrice == null || buyNowPrice > this.currentPrice;
    }

    public boolean isReachedBuyNowPrice(Long bidPrice) {
        return bidPrice >= this.buyNowPrice;
    }

    public void buyNow(User buyer) {
        this.highestBidder = buyer;
        this.status = ItemStatus.BUY_NOW_CLOSED;
    }

    public void closeWithoutBid() {
        this.status = ItemStatus.NO_BID_CLOSED;
    }

    public void closeWithWinner(User winner) {
        this.highestBidder = winner;
        this.status = ItemStatus.BID_CLOSED;
    }
}
