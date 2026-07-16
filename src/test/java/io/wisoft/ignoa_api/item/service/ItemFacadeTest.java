package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockInfrastructureException;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemFacadeTest {

    @Mock
    RedissonDistributedLock redissonDistributedLock;

    @Mock
    ItemCommandService itemCommandService;

    @InjectMocks
    ItemFacade itemFacade;

    @Test
    void Redis_인프라_장애가_발생하면_락_없이_즉시구매를_진행한다() {
        // Given
        long itemId = 1L;
        long buyerId = 2L;
        ItemBuyNowRequest request = new ItemBuyNowRequest(10_000L);
        BuyNowResponse expected = mock(BuyNowResponse.class);

        given(redissonDistributedLock.executeWithLock(
                eq(ItemLockKey.of(itemId)), anyLong(), any(Supplier.class)))
                .willThrow(new LockInfrastructureException("Redis 장애", new RuntimeException()));

        given(itemCommandService.buyNowItem(itemId, buyerId, request)).willReturn(expected);

        // When
        BuyNowResponse response = itemFacade.buyNowItem(itemId, buyerId, request);

        // Then
        assertThat(response).isEqualTo(expected);
        verify(itemCommandService).buyNowItem(itemId, buyerId, request);
    }

    @Test
    void 락_획득이_타임아웃되면_fast_fail되어_예외를_전파한다() {
        // Given
        long itemId = 1L;
        long buyerId = 2L;
        ItemBuyNowRequest request = new ItemBuyNowRequest(10_000L);

        given(redissonDistributedLock.executeWithLock(
                eq(ItemLockKey.of(itemId)), anyLong(), any(Supplier.class)))
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
}
