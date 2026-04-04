package io.wisoft.ignoa_api.wish.entity;

import io.wisoft.ignoa_api.global.common.BaseEntity;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "wishes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "item_id"})
})
public class Wish extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    public static Wish create(User user, Item item) {
        return new Wish(null, user, item);
    }
}
