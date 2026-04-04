package io.wisoft.ignoa_api.item.entity;

import io.wisoft.ignoa_api.global.common.BaseEntity;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "item_media")
public class ItemMedia extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemMediaType mediaType;

    public static ItemMedia from(Item item, String mediaUrl, ItemMediaType mediaType) {
        return new ItemMedia(null, item, mediaUrl, mediaType);
    }
}
