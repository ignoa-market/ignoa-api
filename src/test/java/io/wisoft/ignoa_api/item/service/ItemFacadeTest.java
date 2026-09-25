package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemFacadeTest {

    @Mock
    RedissonDistributedLock redissonDistributedLock;

    @Mock
    ItemCommandService itemCommandService;

    @Mock
    StorageService storageService;

    @InjectMocks
    ItemFacade itemFacade;

    @Test
    void 락_획득이_타임아웃되면_fast_fail되어_예외를_전파한다() {
        // Given
        long itemId = 1L;
        long buyerId = 2L;
        ItemBuyNowRequest request = new ItemBuyNowRequest(10_000L);

        given(redissonDistributedLock.executeWithRequiredLock(
                eq(ItemLockKey.of(itemId)), eq(LockOperation.BUY_NOW), any(Supplier.class)))
                .willThrow(new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> itemFacade.buyNowItem(itemId, buyerId, request)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);
        verify(itemCommandService, never()).buyNowItem(itemId, buyerId, request);
    }

    @Test
    void 동영상_2개로_등록하면_업로드하지_않고_거절한다() {
        // Given
        given(storageService.detectContentType(any(MultipartFile.class))).willReturn("video/mp4");

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> itemFacade.createItem(1L, createRequest(), List.of(file("a.mp4"), file("b.mp4")))
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_VIDEO_LIMIT_EXCEEDED);
        verify(storageService, never()).upload(any(), any());
    }

    @Test
    void 이미지_없이_동영상만으로_등록하면_업로드하지_않고_거절한다() {
        // Given
        given(storageService.detectContentType(any(MultipartFile.class))).willReturn("video/mp4");

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> itemFacade.createItem(1L, createRequest(), List.of(file("a.mp4")))
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_IMAGE_REQUIRED);
        verify(storageService, never()).upload(any(), any());
    }

    private ItemCreateRequest createRequest() {
        return new ItemCreateRequest(
                "테스트 상품", "설명", "카테고리", ItemCondition.GOOD,
                1_000L, 10_000L, "브랜드", LocalDateTime.now().plusDays(1)
        );
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "video/mp4", new byte[]{1});
    }
}
