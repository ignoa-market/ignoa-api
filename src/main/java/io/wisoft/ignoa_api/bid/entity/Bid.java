package io.wisoft.ignoa_api.bid.entity;

import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "bids")
public class Bid extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    public static Bid place(Product product, User bidder, Long price) {
        return new Bid(null, product, bidder, price, BidStatus.ACTIVE);
    }

    public void closeAsWon() {
        this.status = BidStatus.WON;
    }
}
