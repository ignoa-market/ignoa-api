package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.dto.request.ChatMessagePageRequest;
import io.wisoft.ignoa_api.chat.dto.response.ChatMessageResponse;
import io.wisoft.ignoa_api.chat.entity.ChatMessage;
import io.wisoft.ignoa_api.chat.repository.ChatMessageRepository;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageServiceTest extends IntegrationTestSupport {

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    ChatRoomService chatRoomService;

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

    @Autowired
    TransactionTemplate transactionTemplate;

    User seller;
    User buyer;
    Item item;
    Long chatRoomId;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(newUser("seller@test.com", "판매자"));
        buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        item = itemRepository.save(newItem(seller));
        chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();
    }

    @AfterEach
    void tearDown() {
        // chat_rooms.last_message_id와 chat_messages.chat_room_id가 서로를 참조하므로 연결을 먼저 끊는다
        jdbcTemplate.update("UPDATE chat_rooms SET last_message_id = NULL");
        chatMessageRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        // 소프트 삭제된 상품은 @SQLRestriction 때문에 JPA로 지울 수 없어 SQL로 직접 지운다
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 참여자가_메시지를_보내면_저장되고_마지막_메시지가_갱신된다() {
        // When
        ChatMessageResponse response = chatMessageService.sendMessage(chatRoomId, buyer.getId(), "안녕하세요");

        // Then
        assertThat(response.chatRoomId()).isEqualTo(chatRoomId);
        assertThat(response.senderId()).isEqualTo(buyer.getId());
        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(lastMessageId()).isEqualTo(response.messageId());
    }

    @Test
    void 이전_메시지로는_마지막_메시지를_되돌리지_않는다() {
        // Given
        ChatMessageResponse older = chatMessageService.sendMessage(chatRoomId, buyer.getId(), "먼저 보낸 메시지");
        ChatMessageResponse newer = chatMessageService.sendMessage(chatRoomId, seller.getId(), "나중에 보낸 메시지");

        // When: 늦게 도착한 이전 메시지의 갱신 요청
        Integer updatedRows = transactionTemplate.execute(status -> {
            ChatMessage olderMessage = chatMessageRepository.findById(older.messageId()).orElseThrow();
            return chatRoomRepository.updateLastMessageIfNewer(chatRoomId, olderMessage, olderMessage.getId());
        });

        // Then
        assertThat(updatedRows).isZero();
        assertThat(lastMessageId()).isEqualTo(newer.messageId());
    }

    @Test
    void 참여자가_아닌_사용자는_메시지를_보낼_수_없다() {
        // Given
        User other = userRepository.save(newUser("other@test.com", "제3자"));

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendMessage(chatRoomId, other.getId(), "끼어들기"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    @Test
    void 상품이_삭제된_채팅방에는_메시지를_보낼_수_없다() {
        // Given
        transactionTemplate.executeWithoutResult(status -> itemRepository.softDeleteIfActive(item.getId()));

        // When & Then
        assertThatThrownBy(() -> chatMessageService.sendMessage(chatRoomId, buyer.getId(), "삭제 후 메시지"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 메시지가_없는_채팅방은_빈_목록을_반환한다() {
        // When
        SliceResponse<ChatMessageResponse> page = chatMessageService.getMessages(
                chatRoomId, buyer.getId(), new ChatMessagePageRequest(null, null));

        // Then
        assertThat(page.content()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 기준_메시지_ID로_최신부터_과거_방향으로_이어서_조회한다() {
        // Given
        for (int i = 1; i <= 5; i++) {
            chatMessageService.sendMessage(chatRoomId, buyer.getId(), "메시지" + i);
        }

        // When
        SliceResponse<ChatMessageResponse> first = chatMessageService.getMessages(
                chatRoomId, buyer.getId(), new ChatMessagePageRequest(null, 2));
        SliceResponse<ChatMessageResponse> second = chatMessageService.getMessages(
                chatRoomId, buyer.getId(), new ChatMessagePageRequest(lastIdOf(first), 2));
        SliceResponse<ChatMessageResponse> third = chatMessageService.getMessages(
                chatRoomId, buyer.getId(), new ChatMessagePageRequest(lastIdOf(second), 2));

        // Then
        assertThat(first.content()).extracting(ChatMessageResponse::content).containsExactly("메시지5", "메시지4");
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).extracting(ChatMessageResponse::content).containsExactly("메시지3", "메시지2");
        assertThat(second.hasNext()).isTrue();
        assertThat(third.content()).extracting(ChatMessageResponse::content).containsExactly("메시지1");
        assertThat(third.hasNext()).isFalse();
    }

    @Test
    void 참여자가_아닌_사용자는_메시지를_조회할_수_없다() {
        // Given
        User other = userRepository.save(newUser("other@test.com", "제3자"));

        // When & Then
        assertThatThrownBy(() -> chatMessageService.getMessages(
                chatRoomId, other.getId(), new ChatMessagePageRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    private Long lastMessageId() {
        return jdbcTemplate.queryForObject(
                "SELECT last_message_id FROM chat_rooms WHERE id = ?", Long.class, chatRoomId);
    }

    private Long lastIdOf(SliceResponse<ChatMessageResponse> page) {
        return page.content().get(page.content().size() - 1).messageId();
    }
}
