package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemPreviewAssemblerTest {

    @Mock
    ItemMediaService itemMediaService;

    @Mock
    WishRepository wishRepository;

    @InjectMocks
    ItemPreviewAssembler itemPreviewAssembler;

    @Test
    void 찜한_상품만_찜_여부를_채우고_공용_목록은_바꾸지_않는다() {
        // Given
        List<ItemPreview> shared = List.of(preview(1L), preview(2L), preview(3L));
        given(wishRepository.findWishedItemIds(7L, List.of(1L, 2L, 3L))).willReturn(List.of(2L));

        // When
        List<ItemPreview> result = itemPreviewAssembler.applyWished(7L, shared);

        // Then
        assertThat(result).extracting(ItemPreview::isWished).containsExactly(false, true, false);
        assertThat(shared).extracting(ItemPreview::isWished).containsOnly(false);
    }

    @Test
    void 로그인하지_않은_사용자는_찜_여부를_조회하지_않는다() {
        // Given
        List<ItemPreview> shared = List.of(preview(1L));

        // When
        List<ItemPreview> result = itemPreviewAssembler.applyWished(null, shared);

        // Then
        assertThat(result).isSameAs(shared);
        verify(wishRepository, never()).findWishedItemIds(anyLong(), any());
    }

    private ItemPreview preview(Long itemId) {
        return new ItemPreview(itemId, "브랜드", "상품", "url", 1_000L, false, 0,
                ItemStatus.ACTIVE, LocalDateTime.now().plusDays(1), 0);
    }
}
