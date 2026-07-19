package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidFacadeTest {

    @Mock
    RedissonDistributedLock distributedLock;

    @Mock
    BidService bidService;

    @InjectMocks
    BidFacade bidFacade;

    @Test
    void Redis_락_획득이_타임아웃되면_입찰에_실패한다() {
        // Given
        Long itemId = 1L;
        Long bidderId = 2L;
        BidCreateRequest request = new BidCreateRequest(1_000L);

        given(distributedLock.executeWithLockOrFailOpen(eq(ItemLockKey.of(itemId)), anyLong(), any(Supplier.class)))
                .willThrow(new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> bidFacade.placeBid(itemId, bidderId, request)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOCK_ACQUISITION_FAILED);
        verify(bidService, never()).placeBid(itemId, bidderId, request);
    }
}