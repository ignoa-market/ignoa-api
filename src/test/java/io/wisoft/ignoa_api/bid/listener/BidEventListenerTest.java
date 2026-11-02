package io.wisoft.ignoa_api.bid.listener;

import io.wisoft.ignoa_api.bid.dto.response.BidBroadcast;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BidEventListenerTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    BidEventListener bidEventListener;

    @Test
    void 입찰_알림에_입찰_ID를_담아_상품_토픽으로_보낸다() {
        // Given
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 14, 32);
        BidPlaceEvent event = new BidPlaceEvent(1L, 10L, 98_000L, "입찰자", createdAt);

        // When
        bidEventListener.onBidPlaced(event);

        // Then
        ArgumentCaptor<BidBroadcast> captor = ArgumentCaptor.forClass(BidBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/items/1"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new BidBroadcast(1L, 10L, 98_000L, "입찰자", createdAt));
    }

    @Test
    void 입찰가_전송이_실패해도_예외를_던지지_않는다() {
        // Given
        BidPlaceEvent event = new BidPlaceEvent(1L, 10L, 98_000L, "입찰자", LocalDateTime.of(2026, 10, 18, 14, 32));
        willThrow(new MessageDeliveryException("전송 실패"))
                .given(messagingTemplate).convertAndSend(eq("/topic/items/1"), any(BidBroadcast.class));

        // When & Then
        assertThatCode(() -> bidEventListener.onBidPlaced(event)).doesNotThrowAnyException();
    }
}
