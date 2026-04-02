package io.wisoft.ignoa_api.product.entity;

import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "products")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private User winner;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private Long startPrice;

    @Column(nullable = false)
    private Long currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    public static Product create(User seller, String title, String description, String category, Long startPrice, LocalDateTime endTime) {
        return new Product(
                null,
                seller,
                null,
                title,
                description,
                category,
                startPrice,
                startPrice,
                ProductStatus.ON_SALE,
                LocalDateTime.now(),
                endTime
        );
    }

    public void updateInfo(String title, String description, String category, LocalDateTime endTime) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (endTime != null) this.endTime = endTime;
    }

    public void raisePriceTo(Long newPrice) {
        this.currentPrice = newPrice;
    }

    public boolean isOnSale() {
        return this.status == ProductStatus.ON_SALE
                && this.endTime.isAfter(LocalDateTime.now());
    }

    public boolean isSeller(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public boolean isValidBidPrice(Long bidPrice) {
        return this.currentPrice < bidPrice;
    }

    public void closeAsFailed() {
        this.status = ProductStatus.FAILED;
    }

    public void closeWithWinner(User winner) {
        this.winner = winner;
        this.status = ProductStatus.ENDED;
    }

    public boolean isClosed() {
        return this.status != ProductStatus.ON_SALE;
    }
}
