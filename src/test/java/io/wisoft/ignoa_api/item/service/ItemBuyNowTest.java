package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.trade.service.TradeService;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemBuyNowTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ItemCommandService itemCommandService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private BidService bidService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAllInBatch();
        jdbcTemplate.update("UPDATE chat_rooms SET last_message_id = NULL");
        jdbcTemplate.update("DELETE FROM chat_messages");
        chatRoomRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM bids");
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 즉시구매는_결제_대기_거래만_만들고_상품은_판매중으로_둔다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller));

        // When
        BuyNowResponse response = buyNow(item, buyer);

        // Then
        Trade trade = tradeRepository.findById(response.tradeId()).orElseThrow();
        assertThat(trade.getType()).isEqualTo(TradeType.BUY_NOW);
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.PAYMENT_PENDING);
        assertThat(trade.getAmount()).isEqualTo(item.getBuyNowPrice());
        assertThat(statusOf(item)).isEqualTo(ItemStatus.ACTIVE);
        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    void 승인을_시작하면_상품을_잠그고_다른_구매자의_승인은_거래까지_롤백한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User first = userRepository.save(newUser("first@test.com", "first"));
        User second = userRepository.save(newUser("second@test.com", "second"));
        Item item = itemRepository.save(newItem(seller));
        Long firstTradeId = buyNow(item, first).tradeId();
        Long secondTradeId = buyNow(item, second).tradeId();

        // When
        tradeService.startConfirm(firstTradeId, first.getId(), "IGN-first");

        // Then
        assertThat(statusOf(item)).isEqualTo(ItemStatus.BUY_NOW_PENDING);
        assertThatThrownBy(() -> tradeService.startConfirm(secondTradeId, second.getId(), "IGN-second"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUY_NOW_CONFLICT);
        assertThat(tradeStatusOf(secondTradeId)).isEqualTo(TradeStatus.PAYMENT_PENDING);
    }

    @Test
    void 결제가_완료되면_상품을_마감하고_채팅방을_한번만_만든다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller));
        Long tradeId = buyNow(item, buyer).tradeId();
        tradeService.startConfirm(tradeId, buyer.getId(), "IGN-done");
        PaymentResult done = result(tradeId, "IGN-done", "DONE");

        // When: 승인 응답과 콜백이 모두 도착
        boolean firstApplied = tradeService.applyPaymentResult(tradeId, done);
        boolean secondApplied = tradeService.applyPaymentResult(tradeId, done);

        // Then
        assertThat(firstApplied).isTrue();
        assertThat(secondApplied).isFalse();
        assertThat(tradeStatusOf(tradeId)).isEqualTo(TradeStatus.PAID);

        Item closed = itemRepository.findByIdWithSeller(item.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ItemStatus.BUY_NOW_CLOSED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT highest_bidder_id FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(buyer.getId());
        assertThat(chatRoomRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_구매자가_먼저_결제한_상품은_결제_준비_단계에서_막는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User first = userRepository.save(newUser("first@test.com", "first"));
        User second = userRepository.save(newUser("second@test.com", "second"));
        Item item = itemRepository.save(newItem(seller));
        Long firstTradeId = buyNow(item, first).tradeId();
        Long secondTradeId = buyNow(item, second).tradeId();
        tradeService.startConfirm(firstTradeId, first.getId(), "IGN-first");
        tradeService.applyPaymentResult(firstTradeId, result(firstTradeId, "IGN-first", "DONE"));

        // When & Then
        assertThatThrownBy(() -> tradeService.validatePrepare(secondTradeId, second.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUY_NOW_CONFLICT);
    }

    @Test
    void 결제가_실패하면_상품을_다시_판매중으로_되돌린다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller));
        Long tradeId = buyNow(item, buyer).tradeId();
        tradeService.startConfirm(tradeId, buyer.getId(), "IGN-failed");

        // When
        tradeService.applyPaymentResult(tradeId, result(tradeId, "IGN-failed", "FAILED"));

        // Then
        assertThat(tradeStatusOf(tradeId)).isEqualTo(TradeStatus.CANCELED);
        assertThat(statusOf(item)).isEqualTo(ItemStatus.ACTIVE);
        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    void 즉시구매_결제중에도_입찰할_수_있다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        User bidder = userRepository.save(newUser("bidder@test.com", "bidder"));
        Item item = itemRepository.save(newItem(seller));
        Long tradeId = buyNow(item, buyer).tradeId();
        tradeService.startConfirm(tradeId, buyer.getId(), "IGN-pending");

        // When
        bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(2_000L));

        // Then
        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.BUY_NOW_PENDING);
        assertThat(reloaded.getCurrentPrice()).isEqualTo(2_000L);
    }

    private BuyNowResponse buyNow(Item item, User buyer) {
        return itemCommandService.buyNowItem(
                item.getId(), buyer.getId(), new ItemBuyNowRequest(item.getBuyNowPrice()));
    }

    private ItemStatus statusOf(Item item) {
        return itemRepository.findById(item.getId()).orElseThrow().getStatus();
    }

    private TradeStatus tradeStatusOf(Long tradeId) {
        return tradeRepository.findById(tradeId).orElseThrow().getStatus();
    }

    private PaymentResult result(Long tradeId, String orderId, String status) {
        LocalDateTime approvedAt = "DONE".equals(status) ? LocalDateTime.now() : null;
        return new PaymentResult(tradeId, orderId, status, 1_000_000L, approvedAt, null, null);
    }
}
