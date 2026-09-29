package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPreviewTest {

    @Test
    void 상품_목록_응답에_연장_횟수를_포함한다() {
        Item item = Item.create(
                null,
                "테스트 상품",
                "상품 설명",
                "상의",
                ItemCondition.GOOD,
                "IGNOA",
                10_000L,
                20_000L,
                LocalDateTime.now().plusDays(1)
        );

        ItemPreview preview = ItemPreview.from(item, "image.jpg", 0, false);

        assertThat(preview.extensionCount()).isZero();
    }
}
