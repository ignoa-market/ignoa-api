package io.wisoft.ignoa_api.chat;

import io.wisoft.ignoa_api.auth.jwt.JwtTokenProvider;
import io.wisoft.ignoa_api.auth.service.TokenBlacklistService;
import io.wisoft.ignoa_api.chat.repository.ChatMessageRepository;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.chat.service.ChatMessageService;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.infra.redis.RedisInfrastructureException;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest extends IntegrationTestSupport {

    private static final String CHAT_QUEUE = "/user/queue/chat";

    @LocalServerPort
    int port;

    @MockitoBean
    TokenBlacklistService tokenBlacklistService;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    ChatRoomService chatRoomService;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        given(tokenBlacklistService.isBlacklisted(anyString())).willReturn(false);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("UPDATE chat_rooms SET last_message_id = NULL");
        chatMessageRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        // 소프트 삭제된 상품은 @SQLRestriction 때문에 JPA로 지울 수 없어 SQL로 직접 지운다
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 메시지는_채팅방의_두_참여자에게만_전달된다() throws Exception {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        User other = userRepository.save(newUser("other@test.com", "제3자"));
        Item item = itemRepository.save(newItem(seller));
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        BlockingQueue<Map<String, Object>> sellerInbox = subscribe(connect(bearer(seller)), CHAT_QUEUE);
        BlockingQueue<Map<String, Object>> buyerInbox = subscribe(connect(bearer(buyer)), CHAT_QUEUE);
        BlockingQueue<Map<String, Object>> otherInbox = subscribe(connect(bearer(other)), CHAT_QUEUE);
        waitForSubscriptions();

        // When
        chatMessageService.sendMessage(chatRoomId, buyer.getId(), "안녕하세요");

        // Then
        assertThat(sellerInbox.poll(3, TimeUnit.SECONDS))
                .containsEntry("chat_room_id", chatRoomId.intValue())
                .containsEntry("content", "안녕하세요");
        assertThat(buyerInbox.poll(3, TimeUnit.SECONDS)).containsEntry("content", "안녕하세요");
        assertThat(otherInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void 토큰_없이도_연결할_수_있지만_채팅은_받지_못한다() throws Exception {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        StompSession anonymous = connect(null);
        BlockingQueue<Map<String, Object>> anonymousInbox = subscribe(anonymous, CHAT_QUEUE);
        waitForSubscriptions();

        // When
        chatMessageService.sendMessage(chatRoomId, buyer.getId(), "안녕하세요");

        // Then
        assertThat(anonymous.isConnected()).isTrue();
        assertThat(anonymousInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void 유효하지_않은_토큰이면_연결을_거절한다() {
        assertThatThrownBy(() -> connect("Bearer invalid.token.value"))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("Token " + jwtTokenProvider.createAccessToken(1L)))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void 로그아웃된_토큰이면_연결을_거절한다() {
        // Given
        String token = jwtTokenProvider.createAccessToken(1L);
        given(tokenBlacklistService.isBlacklisted(eq(token))).willReturn(true);

        // When & Then
        assertThatThrownBy(() -> connect("Bearer " + token))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void Redis_장애로_토큰을_확인할_수_없으면_연결을_거절한다() {
        // Given
        String token = jwtTokenProvider.createAccessToken(1L);
        given(tokenBlacklistService.isBlacklisted(eq(token)))
                .willThrow(new RedisInfrastructureException("Redis 명령 시간 초과", null));

        // When & Then
        assertThatThrownBy(() -> connect("Bearer " + token))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void 클라이언트의_SEND는_차단되고_상대에게_전달되지_않는다() throws Exception {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User attacker = userRepository.save(newUser("attacker@test.com", "공격자"));
        BlockingQueue<Map<String, Object>> sellerInbox = subscribe(connect(bearer(seller)), CHAT_QUEUE);
        StompSession attackerSession = connect(bearer(attacker));
        waitForSubscriptions();

        // When
        attackerSession.send("/user/" + seller.getId() + "/queue/chat", Map.of("content", "위조 메시지"));

        // Then
        assertThat(sellerInbox.poll(1, TimeUnit.SECONDS)).isNull();
        assertDisconnected(attackerSession);
    }

    @Test
    void 개인_큐의_실제_주소를_직접_구독하면_차단된다() throws Exception {
        // Given
        User user = userRepository.save(newUser("user@test.com", "사용자"));
        StompSession session = connect(bearer(user));

        // When
        subscribe(session, "/queue/chat-user0");

        // Then
        assertDisconnected(session);
    }

    private StompSession connect(String authorization) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }

        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(3, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });
        return inbox;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    // SUBSCRIBE는 응답 없이 비동기로 등록되므로 서버에 반영될 시간을 준다
    private void waitForSubscriptions() throws InterruptedException {
        Thread.sleep(300);
    }

    private void assertDisconnected(StompSession session) throws InterruptedException {
        for (int i = 0; i < 20 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
        assertThat(session.isConnected()).isFalse();
    }
}
