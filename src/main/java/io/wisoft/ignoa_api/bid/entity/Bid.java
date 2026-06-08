package io.wisoft.ignoa_api.bid.entity;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.common.BaseEntity;
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
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    public static Bid place(Item item, User bidder, Long price) {
        return new Bid(null, item, bidder, price, BidStatus.ACTIVE);
    }

    public boolean isTopBid(Item item) {
        return this.price.equals(item.getCurrentPrice());
    }


    public void closeAsWon() {
        this.status = BidStatus.WON;
    }

    public void closeAsLost() {
        this.status = BidStatus.LOST;
    }
}
