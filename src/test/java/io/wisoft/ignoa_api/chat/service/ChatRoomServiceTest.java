package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.dto.response.ChatRoomPreview;
import io.wisoft.ignoa_api.chat.repository.ChatMessageRepository;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.trade.service.TradePaymentService;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemCommandService;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomServiceTest extends IntegrationTestSupport {

    @Autowired
    ChatRoomService chatRoomService;

    @Autowired
    ChatMessageService chatMessageService;

    @Autowired
    ItemCommandService itemCommandService;

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
    TradePaymentService tradePaymentService;

    @Autowired
    TradeRepository tradeRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAllInBatch();
        // chat_rooms.last_message_id와 chat_messages.chat_room_id가 서로를 참조하므로 연결을 먼저 끊는다
        jdbcTemplate.update("UPDATE chat_rooms SET last_message_id = NULL");
        chatMessageRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        // 소프트 삭제된 상품은 @SQLRestriction 때문에 JPA로 지울 수 없어 SQL로 직접 지운다
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 경매_중인_상품에서_채팅방을_열면_채팅방이_생성된다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));

        // When
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // Then
        assertThat(chatRoomRepository.findById(chatRoomId)).isPresent();
    }

    @Test
    void 같은_상품에서_다시_채팅방을_열면_기존_채팅방을_반환한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        Long first = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // When
        Long second = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // Then
        assertThat(second).isEqualTo(first);
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    void 판매자_본인은_자기_상품에서_채팅방을_열_수_없다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Item item = itemRepository.save(newItem(seller));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.openChatRoom(seller.getId(), item.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_CHAT_NOT_ALLOWED);
    }

    @Test
    void 마감된_상품에서는_새_채팅방을_열_수_없다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(1)));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.openChatRoom(buyer.getId(), item.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUCTION_ALREADY_CLOSED);
    }

    @Test
    void 채팅방이_있는_사용자가_즉시구매하면_기존_채팅방을_재사용한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // When: 즉시구매 결제가 완료되면 채팅방을 만든다
        Long tradeId = itemCommandService.buyNowItem(
                item.getId(), buyer.getId(), new ItemBuyNowRequest(item.getBuyNowPrice())).tradeId();
        tradePaymentService.startConfirm(tradeId, buyer.getId(), "IGN-chat");
        tradePaymentService.applyPaymentResult(tradeId, new PaymentResult(
                tradeId, "IGN-chat", "DONE", item.getBuyNowPrice(), LocalDateTime.now(), null, null));

        // Then
        assertThat(chatRoomRepository.findAll())
                .singleElement()
                .extracting("id")
                .isEqualTo(chatRoomId);
    }

    @Test
    void 채팅방_목록은_마지막_메시지_순으로_정렬하고_메시지가_없으면_생성_시간을_기준으로_한다() throws InterruptedException {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Long first = openRoom(seller, buyer);
        Thread.sleep(10);
        Long second = openRoom(seller, buyer);
        Thread.sleep(10);
        Long third = openRoom(seller, buyer);
        Thread.sleep(10);
        chatMessageService.sendMessage(first, buyer.getId(), "가장 최근 대화");

        // When
        List<ChatRoomPreview> rooms = chatRoomService.getChatRooms(buyer.getId());

        // Then
        assertThat(rooms).extracting(ChatRoomPreview::chatRoomId)
                .containsExactly(first, third, second);
        assertThat(rooms.get(0).lastMessage()).isEqualTo("가장 최근 대화");
        assertThat(rooms.get(1).lastMessage()).isNull();
    }

    @Test
    void 채팅방_목록은_사용자_기준으로_상대방과_역할을_표시한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        openRoom(seller, buyer);

        // When
        ChatRoomPreview sellerView = chatRoomService.getChatRooms(seller.getId()).get(0);
        ChatRoomPreview buyerView = chatRoomService.getChatRooms(buyer.getId()).get(0);

        // Then
        assertThat(sellerView.partnerId()).isEqualTo(buyer.getId());
        assertThat(sellerView.role()).isEqualTo("SELLER");
        assertThat(buyerView.partnerId()).isEqualTo(seller.getId());
        assertThat(buyerView.role()).isEqualTo("BUYER");
    }

    @Test
    void 참여자가_아닌_사용자는_채팅방을_조회할_수_없다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        User other = userRepository.save(newUser("other@test.com", "제3자"));
        Long chatRoomId = openRoom(seller, buyer);

        // When & Then
        assertThatThrownBy(() -> chatRoomService.getChatRoom(chatRoomId, other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
    }

    @Test
    void 상품이_삭제되면_채팅방은_목록과_상세에서_조회되지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // When
        transactionTemplate.executeWithoutResult(status -> itemRepository.softDeleteIfActive(item.getId()));

        // Then
        assertThat(chatRoomService.getChatRooms(buyer.getId())).isEmpty();
        assertThatThrownBy(() -> chatRoomService.getChatRoom(chatRoomId, buyer.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 상품에_내_채팅방이_있으면_채팅방_ID를_반환한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        Long chatRoomId = chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();

        // When
        Long found = chatRoomService.checkMyChatRoom(item.getId(), buyer.getId()).chatRoomId();

        // Then
        assertThat(found).isEqualTo(chatRoomId);
    }

    @Test
    void 상품에_내_채팅방이_없으면_null을_반환하고_채팅방을_만들지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));

        // When
        Long found = chatRoomService.checkMyChatRoom(item.getId(), buyer.getId()).chatRoomId();

        // Then
        assertThat(found).isNull();
        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    void 판매자가_자기_상품을_조회하면_null을_반환한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        chatRoomService.openChatRoom(buyer.getId(), item.getId());

        // When
        Long found = chatRoomService.checkMyChatRoom(item.getId(), seller.getId()).chatRoomId();

        // Then
        assertThat(found).isNull();
    }

    @Test
    void 삭제된_상품의_채팅방은_조회되지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User buyer = userRepository.save(newUser("buyer@test.com", "구매자"));
        Item item = itemRepository.save(newItem(seller));
        chatRoomService.openChatRoom(buyer.getId(), item.getId());
        transactionTemplate.executeWithoutResult(status -> itemRepository.softDeleteIfActive(item.getId()));

        // When & Then
        assertThatThrownBy(() -> chatRoomService.checkMyChatRoom(item.getId(), buyer.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    private Long openRoom(User seller, User buyer) {
        Item item = itemRepository.save(newItem(seller));
        return chatRoomService.openChatRoom(buyer.getId(), item.getId()).chatRoomId();
    }
}
