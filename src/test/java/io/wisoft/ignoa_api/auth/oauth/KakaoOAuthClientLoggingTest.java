package io.wisoft.ignoa_api.auth.oauth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KakaoOAuthClientLoggingTest {

    @Test
    void 카카오_인증_요청_거절은_개별_WARN_대신_DEBUG로_기록한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kakao.test/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        KakaoProperties properties = new KakaoProperties(
                "client", "secret", "https://example.test/callback",
                "https://kakao.test/token", "https://kakao.test/user");
        KakaoOAuthClient client = new KakaoOAuthClient(properties, builder.build());

        try (LogCapture logs = new LogCapture()) {
            assertThatThrownBy(() -> client.getAccessToken("invalid-code"))
                    .isInstanceOf(BusinessException.class);
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage()).contains("operation=TOKEN_EXCHANGE", "status=400");
            });
        }
        server.verify();
    }

    @Test
    void 카카오_사용자_정보_요청_거절도_DEBUG로_기록한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kakao.test/user"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        KakaoProperties properties = new KakaoProperties(
                "client", "secret", "https://example.test/callback",
                "https://kakao.test/token", "https://kakao.test/user");
        KakaoOAuthClient client = new KakaoOAuthClient(properties, builder.build());

        try (LogCapture logs = new LogCapture()) {
            assertThatThrownBy(() -> client.getUserInfo("invalid-token"))
                    .isInstanceOf(BusinessException.class);
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage()).contains("operation=USER_INFO", "status=400");
            });
        }
        server.verify();
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(KakaoOAuthClient.class);
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
