package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
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
    void 락_획득이_타임아웃되면_fast_fail되어_예외를_전파한다() {
        // Given
        long itemId = 1L;
        long buyerId = 2L;
        ItemBuyNowRequest request = new ItemBuyNowRequest(10_000L);

        given(redissonDistributedLock.executeWithLockOrFailOpen(
                eq(ItemLockKey.of(itemId)), eq(LockOperation.BUY_NOW), anyLong(), any(Supplier.class)))
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
