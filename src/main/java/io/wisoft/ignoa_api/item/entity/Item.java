package io.wisoft.ignoa_api.item.entity;

import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SQLRestriction("status <> 'DELETED'")
@Table(name = "items",
        indexes = {
                // 사용 쿼리 미확인 (최신순이 id 정렬로 바뀐 뒤 남은 인덱스로 추정)
                @Index(name = "idx_items_status_created", columnList = "status, created_at"),

                // 마감 임박순 목록, 만료 경매 조회·집계 (자동 마감)
                @Index(name = "idx_items_status_end_at", columnList = "status, end_at"),

                // 최신순 목록 (커서 이어 보기)
                @Index(name = "idx_items_status_id", columnList = "status, id"),

                // 인기순 목록 (입찰자 수 → 찜 수 → 최신)
                @Index(name = "idx_items_status_bidder_count_wish_count_id", columnList = "status, bidder_count, wish_count, id")

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

    @Column(nullable = false)
    private int extensionCount;

    @Column(nullable = false)
    private int bidderCount;

    @Column(nullable = false)
    private int wishCount;

    public static Item create(User seller, String title, String description, String category, ItemCondition itemCondition, String brand, Long startPrice, Long buyNowPrice, LocalDateTime endAt) {
        return new Item(
                null, seller, null,
                title, description, category, itemCondition,
                startPrice, startPrice, buyNowPrice,
                brand, ItemStatus.ACTIVE, endAt, 0, 0, 0
        );
    }

    public void update(String title, String description, String category, String brand, ItemCondition itemCondition, Long buyNowPrice) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (brand != null) this.brand = brand;
        if (itemCondition != null) this.itemCondition = itemCondition;
        if (buyNowPrice != null) this.buyNowPrice = buyNowPrice;
    }

    public boolean isSeller(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public boolean isActive() {
        return this.status == ItemStatus.ACTIVE
                && this.endAt.isAfter(LocalDateTime.now());
    }

    public boolean isValidBuyNowPrice(Long buyNowPrice) {
        return buyNowPrice == null
                || buyNowPrice > this.currentPrice;
    }

    public boolean isBuyNowPriceChanged(Long buyNowPrice) {
        return buyNowPrice != null
                && !buyNowPrice.equals(this.buyNowPrice);
    }
}
