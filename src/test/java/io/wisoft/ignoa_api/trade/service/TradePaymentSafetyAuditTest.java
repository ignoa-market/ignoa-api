package io.wisoft.ignoa_api.trade.service;

import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemCommandService;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

class TradePaymentSafetyAuditTest extends IntegrationTestSupport {

    @Autowired UserRepository users;
    @Autowired ItemRepository items;
    @Autowired TradeRepository trades;
    @Autowired ItemCommandService itemCommands;
    @Autowired TradePaymentService payments;
    @Autowired PaymentResultApplier applier;
    @Autowired TradeService tradeService;
    @Autowired ChatRoomRepository chats;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean ChatRoomService chatService;

    @AfterEach
    void cleanup() {
        trades.deleteAllInBatch();
        jdbc.update("UPDATE chat_rooms SET last_message_id = NULL");
        jdbc.update("DELETE FROM chat_messages");
        chats.deleteAllInBatch();
        jdbc.update("DELETE FROM bids");
        items.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void 이전_거래의_늦은_결과는_새_거래의_상품_예약을_건드리지_않는다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User a = users.save(newUser("a@test.com", "a"));
        User b = users.save(newUser("b@test.com", "b"));
        Item item = items.save(newItem(seller));
        Long first = buy(item, a);
        Long second = buy(item, b);
        payments.startConfirm(first, a.getId(), "IGN-first");
        assertThat(applier.apply(first, result(first, "IGN-first", "FAILED"))).isTrue();
        payments.startConfirm(second, b.getId(), "IGN-second");

        assertThat(applier.apply(first, result(first, "IGN-first", "FAILED"))).isFalse();
        assertThat(applier.apply(first, result(first, "IGN-first", "DONE"))).isFalse();
        payments.cancelConfirm(first, "IGN-first");

        assertThat(items.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(ItemStatus.BUY_NOW_RESERVED);
        assertThat(trades.findById(second).orElseThrow().getStatus())
                .isEqualTo(TradeStatus.PAYMENT_CONFIRMING);
        assertThat(applier.apply(second, result(second, "IGN-second", "DONE"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT highest_bidder_id FROM items WHERE id = ?",
                Long.class, item.getId())).isEqualTo(b.getId());
    }

    @Test
    void 상품_후처리_예외는_거래와_상품을_함께_롤백하고_같은_결과로_재처리할_수_있다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User buyer = users.save(newUser("buyer@test.com", "buyer"));
        Item item = items.save(newItem(seller));
        Long trade = buy(item, buyer);
        payments.startConfirm(trade, buyer.getId(), "IGN-rollback");
        PaymentResult done = result(trade, "IGN-rollback", "DONE");
        doThrow(new IllegalStateException("injected post-processing failure"))
                .when(chatService).createChatRoom(item.getId());

        assertThatThrownBy(() -> applier.apply(trade, done)).isInstanceOf(IllegalStateException.class);
        assertThat(trades.findById(trade).orElseThrow().getStatus())
                .isEqualTo(TradeStatus.PAYMENT_CONFIRMING);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(ItemStatus.BUY_NOW_RESERVED);
        assertThat(chats.count()).isZero();

        doCallRealMethod().when(chatService).createChatRoom(item.getId());
        assertThat(applier.apply(trade, done)).isTrue();
        assertThat(applier.apply(trade, done)).isFalse();
        assertThat(trades.findById(trade).orElseThrow().getStatus()).isEqualTo(TradeStatus.PAID);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.BUY_NOW_SOLD);
        assertThat(chats.count()).isEqualTo(1);
    }

    @Test
    void 중복_즉시구매는_거래를_추가하지만_같은_상품의_두번째_승인은_차단된다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User buyer = users.save(newUser("buyer@test.com", "buyer"));
        Item item = items.save(newItem(seller));
        Long first = buy(item, buyer);
        Long second = buy(item, buyer);

        assertThat(first).isNotEqualTo(second);
        payments.startConfirm(first, buyer.getId(), "IGN-first");
        assertThatThrownBy(() -> payments.startConfirm(second, buyer.getId(), "IGN-second"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUY_NOW_CONFLICT);
        assertThat(trades.findById(second).orElseThrow().getStatus()).isEqualTo(TradeStatus.PAYMENT_PENDING);
        assertThat(applier.apply(first, result(first, "IGN-first", "DONE"))).isTrue();

        // 새 거래가 있어도 실제 결제한 거래 ID로 결과를 확인한다.
        var paid = tradeService.getTrade(first, buyer.getId());
        assertThat(paid.tradeId()).isEqualTo(first);
        assertThat(paid.status()).isEqualTo("PAID");
        var pending = tradeService.getTrade(second, buyer.getId());
        assertThat(pending.tradeId()).isEqualTo(second);
        assertThat(pending.status()).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    void 거래_단건_조회는_다른_구매자의_접근을_거절한다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User buyer = users.save(newUser("buyer@test.com", "buyer"));
        User other = users.save(newUser("other@test.com", "other"));
        Item item = items.save(newItem(seller));
        Long tradeId = buy(item, buyer);

        assertThatThrownBy(() -> tradeService.getTrade(tradeId, other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRADE_ACCESS_DENIED);
    }

    @Test
    void 존재하지_않는_거래_단건_조회는_NOT_FOUND를_반환한다() {
        User buyer = users.save(newUser("buyer@test.com", "buyer"));

        assertThatThrownBy(() -> tradeService.getTrade(Long.MAX_VALUE, buyer.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRADE_NOT_FOUND);
    }

    @Test
    void 낙찰_조회는_더_최근의_즉시구매_거래를_제외하고_낙찰_거래만_반환한다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User buyer = users.save(newUser("buyer@test.com", "buyer"));
        Item item = items.save(newItem(seller));
        Trade auction = trades.save(Trade.create(item, buyer, TradeType.AUCTION,
                10_000L, LocalDateTime.now().plusHours(24)));
        trades.save(Trade.create(item, buyer, TradeType.BUY_NOW,
                item.getBuyNowPrice(), LocalDateTime.now().plusMinutes(30)));

        var actual = tradeService.getMyAuctionTrade(item.getId(), buyer.getId());

        assertThat(actual.tradeId()).isEqualTo(auction.getId());
        assertThat(actual.type()).isEqualTo("AUCTION");
    }

    @Test
    void 즉시구매_거래만_있으면_낙찰_조회는_NOT_FOUND를_반환한다() {
        User seller = users.save(newUser("seller@test.com", "seller"));
        User buyer = users.save(newUser("buyer@test.com", "buyer"));
        Item item = items.save(newItem(seller));
        buy(item, buyer);

        assertThatThrownBy(() -> tradeService.getMyAuctionTrade(item.getId(), buyer.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRADE_NOT_FOUND);
    }

    private Long buy(Item item, User buyer) {
        return itemCommands.buyNowItem(item.getId(), buyer.getId(),
                new ItemBuyNowRequest(item.getBuyNowPrice())).tradeId();
    }

    private PaymentResult result(Long tradeId, String orderId, String status) {
        return new PaymentResult(tradeId, orderId, status, 1_000_000L,
                "DONE".equals(status) ? LocalDateTime.now() : null, null, null);
    }
}
