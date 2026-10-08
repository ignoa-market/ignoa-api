package io.wisoft.ignoa_api.user.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.wisoft.ignoa_api.auth.service.RefreshTokenService;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.infra.storage.StorageUploadResult;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserFacadeLoggingTest {

    @Test
    void 프로필_이미지_보상_Outbox_적재_실패는_userId와_수동_확인_대상을_기록한다() {
        UserCommandService commandService = mock(UserCommandService.class);
        StorageService storageService = mock(StorageService.class);
        OutboxAppender outboxAppender = mock(OutboxAppender.class);
        UserFacade facade = new UserFacade(commandService, storageService,
                mock(MediaUrlResolver.class), outboxAppender,
                mock(RefreshTokenService.class), mock(TokenBlacklistService.class));
        MockMultipartFile file = new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[]{1});

        when(storageService.upload(any(), any()))
                .thenReturn(new StorageUploadResult("profiles/test.jpg", "image/jpeg"));
        when(commandService.replaceProfileImage(7L, "profiles/test.jpg"))
                .thenThrow(new IllegalStateException("DB 반영 실패"));
        doThrow(new IllegalStateException("Outbox 적재 실패"))
                .when(outboxAppender).saveForCompensation(anyString(), anyString(), anyString(), any());

        try (LogCapture logs = new LogCapture()) {
            assertThatThrownBy(() -> facade.updateProfileImage(7L, file))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                        .contains("userId=7", "objectKey=profiles/test.jpg", "action=업로드 파일 수동 확인")
                        .doesNotContain("aggregateId=");
            });
        }
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(UserFacade.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture() {
            appender.start();
            logger.addAppender(appender);
        }

        private java.util.List<ILoggingEvent> events() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
