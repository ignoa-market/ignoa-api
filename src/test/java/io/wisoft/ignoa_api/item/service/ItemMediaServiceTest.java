package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ItemMediaServiceTest {

    private static final Long ITEM_ID = 1L;

    @Mock
    ItemMediaRepository itemMediaRepository;

    @InjectMocks
    ItemMediaService itemMediaService;

    @Test
    void 기존_동영상이_있는데_동영상을_추가하면_거절한다() {
        // Given
        given(itemMediaRepository.findAllByItemId(ITEM_ID))
                .willReturn(List.of(media(1L, ItemMediaType.IMAGE), media(2L, ItemMediaType.VIDEO)));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> itemMediaService.validateMediaComposition(ITEM_ID, List.of(), List.of(uploaded(ItemMediaType.VIDEO)))
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_VIDEO_LIMIT_EXCEEDED);
    }

    @Test
    void 기존_동영상을_삭제하고_동영상을_추가하면_통과한다() {
        // Given
        given(itemMediaRepository.findAllByItemId(ITEM_ID))
                .willReturn(List.of(media(1L, ItemMediaType.IMAGE), media(2L, ItemMediaType.VIDEO)));

        // When & Then
        assertThatCode(() -> itemMediaService.validateMediaComposition(
                ITEM_ID, List.of(2L), List.of(uploaded(ItemMediaType.VIDEO))
        )).doesNotThrowAnyException();
    }

    @Test
    void 유일한_이미지를_삭제하고_동영상만_남으면_거절한다() {
        // Given
        given(itemMediaRepository.findAllByItemId(ITEM_ID))
                .willReturn(List.of(media(1L, ItemMediaType.IMAGE)));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> itemMediaService.validateMediaComposition(ITEM_ID, List.of(1L), List.of(uploaded(ItemMediaType.VIDEO)))
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_IMAGE_REQUIRED);
    }

    @Test
    void 삭제_목록이_없으면_기존_미디어로만_판단한다() {
        // Given
        given(itemMediaRepository.findAllByItemId(ITEM_ID))
                .willReturn(List.of(media(1L, ItemMediaType.IMAGE)));

        // When & Then
        assertThatCode(() -> itemMediaService.validateMediaComposition(ITEM_ID, null, List.of()))
                .doesNotThrowAnyException();
    }

    private ItemMedia media(Long id, ItemMediaType mediaType) {
        ItemMedia itemMedia = ItemMedia.from(null, "items/" + id, mediaType);
        ReflectionTestUtils.setField(itemMedia, "id", id);
        return itemMedia;
    }

    private UploadedMedia uploaded(ItemMediaType mediaType) {
        return new UploadedMedia("items/new", mediaType);
    }
}
