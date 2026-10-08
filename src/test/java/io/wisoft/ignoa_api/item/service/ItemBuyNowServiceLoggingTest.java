package io.wisoft.ignoa_api.item.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemBuyNowServiceLoggingTest {

    @Test
    void 예약_거절_로그는_확인하지_않은_원인을_단정하지_않는다() {
        ItemBuyNowService service = new ItemBuyNowService(
                mock(ItemRepository.class), mock(BidRepository.class), mock(ChatRoomService.class));

        try (LogCapture logs = new LogCapture()) {
            assertThat(catchThrowableOfType(BusinessException.class,
                    () -> service.reserve(1L, 10_000L, LocalDateTime.now())))
                    .isNotNull();

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage()).contains("reason=예약 조건 불충족");
            });
        }
    }

    @Test
    void 정상_즉시구매_마감은_개별_INFO가_아닌_DEBUG로_기록한다() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        BidRepository bidRepository = mock(BidRepository.class);
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(2L);
        when(itemRepository.completeBuyNowIfPending(1L, buyer)).thenReturn(1);
        try (LogCapture logs = new LogCapture()) {
            new ItemBuyNowService(itemRepository, bidRepository, mock(ChatRoomService.class))
                    .complete(1L, buyer);

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage()).contains("즉시구매 상품 마감 완료", "itemId=1");
            });
        }
    }

    @Test
    void 예약_해제와_해제_생략은_개별_DEBUG로_기록한다() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        when(itemRepository.cancelBuyNowIfPending(1L)).thenReturn(1);
        ItemBuyNowService service = new ItemBuyNowService(
                itemRepository, mock(BidRepository.class), mock(ChatRoomService.class));

        try (LogCapture logs = new LogCapture()) {
            service.cancel(1L);
            service.cancel(2L);

            assertThat(logs.events()).hasSize(2);
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
            assertThat(logs.events().get(1).getFormattedMessage())
                    .contains("reason=결제 대기 상태 아님");
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(ItemBuyNowService.class);
        private final Level previousLevel = logger.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture() {
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);
        }

        private java.util.List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
