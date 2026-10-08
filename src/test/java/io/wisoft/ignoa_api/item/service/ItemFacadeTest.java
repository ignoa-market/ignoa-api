package io.wisoft.ignoa_api.item.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.global.infra.storage.StorageUploadResult;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ItemFacadeTest {

    @Mock
    RedissonDistributedLock redissonDistributedLock;

    @Mock
    ItemCommandService itemCommandService;

    @Mock
    StorageService storageService;

    @Mock
    OutboxAppender outboxAppender;

    @InjectMocks
    ItemFacade itemFacade;

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

    @Test
    void 등록_보상_Outbox_적재_실패_로그에는_sellerId를_남긴다() {
        given(storageService.detectContentType(any(MultipartFile.class))).willReturn("image/jpeg");
        given(storageService.upload(any(MultipartFile.class), any()))
                .willReturn(new StorageUploadResult("items/test.jpg", "image/jpeg"));
        given(itemCommandService.createItem(eq(7L), any(), any()))
                .willThrow(new IllegalStateException("상품 저장 실패"));
        doThrow(new IllegalStateException("Outbox 저장 실패"))
                .when(outboxAppender).saveForCompensation(anyString(), anyString(), anyString(), any());

        try (LogCapture logs = new LogCapture()) {
            assertThatThrownBy(() -> itemFacade.createItem(7L, createRequest(), List.of(file("test.jpg"))))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.errorMessages()).singleElement()
                    .asString()
                    .contains("sellerId=7", "objectKey=items/test.jpg", "action=업로드 파일 수동 확인")
                    .doesNotContain("aggregateId=");
        }
    }

    @Test
    void 수정_보상_Outbox_적재_실패_로그에는_itemId를_남긴다() {
        given(storageService.upload(any(MultipartFile.class), any()))
                .willReturn(new StorageUploadResult("items/test.jpg", "image/jpeg"));
        given(redissonDistributedLock.executeWithRequiredLock(anyString(), any(), any()))
                .willThrow(new IllegalStateException("상품 수정 실패"));
        doThrow(new IllegalStateException("Outbox 저장 실패"))
                .when(outboxAppender).saveForCompensation(anyString(), anyString(), anyString(), any());

        try (LogCapture logs = new LogCapture()) {
            assertThatThrownBy(() -> itemFacade.updateItem(9L, 7L,
                    new ItemUpdateRequest("상품", "설명", "카테고리", "브랜드", ItemCondition.GOOD, 10_000L, List.of()),
                    List.of(file("test.jpg"))))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.errorMessages()).singleElement()
                    .asString()
                    .contains("itemId=9", "objectKey=items/test.jpg", "action=업로드 파일 수동 확인")
                    .doesNotContain("aggregateId=");
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(ItemFacade.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture() {
            appender.start();
            logger.addAppender(appender);
        }

        private List<String> errorMessages() {
            return appender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
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
