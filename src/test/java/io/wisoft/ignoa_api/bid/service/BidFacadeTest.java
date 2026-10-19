package io.wisoft.ignoa_api.bid.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.metric.BidMetrics;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidFacadeTest {

    private static final Long ITEM_ID = 1L;
    private static final Long BIDDER_ID = 2L;
    private static final BidCreateRequest REQUEST = new BidCreateRequest(1_000L);

    @Mock
    RedissonDistributedLock distributedLock;

    @Mock
    BidService bidService;

    SimpleMeterRegistry registry;
    BidFacade bidFacade;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        bidFacade = new BidFacade(bidService, new BidMetrics(registry), distributedLock);
    }

    @Test
    void 입찰은_분산_락_없이_서비스를_직접_호출한다() {
        // Given
        BidResponse response = new BidResponse(10L, ITEM_ID, BIDDER_ID, 1_000L, LocalDateTime.now());
        given(bidService.placeBid(ITEM_ID, BIDDER_ID, REQUEST)).willReturn(response);

        // When
        BidResponse result = bidFacade.placeBid(ITEM_ID, BIDDER_ID, REQUEST);

        // Then
        assertThat(result).isSameAs(response);
        verify(bidService).placeBid(ITEM_ID, BIDDER_ID, REQUEST);
        verifyNoInteractions(distributedLock);
    }

    @Test
    void 입찰이_반영되면_반영_건수만_센다() {
        // Given
        BidResponse response = new BidResponse(10L, ITEM_ID, BIDDER_ID, 1_000L, LocalDateTime.now());
        given(bidService.placeBid(ITEM_ID, BIDDER_ID, REQUEST)).willReturn(response);

        // When
        BidResponse result = bidFacade.placeBid(ITEM_ID, BIDDER_ID, REQUEST);

        // Then
        assertThat(result).isEqualTo(response);
        assertThat(count("accepted")).isEqualTo(1.0);
        assertThat(count("price_conflict")).isZero();
    }

    @Test
    void 가격_경합으로_거절되면_거절_건수를_세고_같은_예외를_던진다() {
        // Given
        BusinessException expected = new BusinessException(ErrorCode.BID_CONFLICT);
        given(bidService.placeBid(ITEM_ID, BIDDER_ID, REQUEST)).willThrow(expected);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> bidFacade.placeBid(ITEM_ID, BIDDER_ID, REQUEST)
        );

        // Then
        assertThat(exception).isSameAs(expected);
        assertThat(count("price_conflict")).isEqualTo(1.0);
        assertThat(count("accepted")).isZero();
    }

    @Test
    void 본인_입찰은_입찰_결과로_세지_않고_같은_예외를_던진다() {
        // Given
        BusinessException expected = new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
        given(bidService.placeBid(ITEM_ID, BIDDER_ID, REQUEST)).willThrow(expected);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> bidFacade.placeBid(ITEM_ID, BIDDER_ID, REQUEST)
        );

        // Then
        assertThat(exception).isSameAs(expected);
        assertThat(count("accepted")).isZero();
        assertThat(count("price_conflict")).isZero();
    }

    private double count(String outcome) {
        return registry.get("bid.place").tag("outcome", outcome).counter().count();
    }
}
