package io.wisoft.ignoa_api.trade.scheduler;

import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemCommandService;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.payment.PaymentClient;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.trade.service.TradePaymentService;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TradeResolveJobTest extends IntegrationTestSupport {

    @MockitoBean
    private PaymentClient paymentClient;

    @Autowired
    private TradeResolveJob tradeResolveJob;

    @Autowired
    private TradePaymentService tradePaymentService;

    @Autowired
    private ItemCommandService itemCommandService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

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
    void 결제_서버가_완료면_거래를_PAID로_반영하고_상품을_마감한다() {
        // Given
        Fixture f = stuckBuyNowTrade("IGN-done");
        given(paymentClient.getPayment("IGN-done")).willReturn(result(f.tradeId, "IGN-done", "DONE"));

        // When
        tradeResolveJob.resolve();

        // Then
        assertThat(tradeStatusOf(f.tradeId)).isEqualTo(TradeStatus.PAID);
        assertThat(itemStatusOf(f.itemId)).isEqualTo(ItemStatus.BUY_NOW_CLOSED);
    }

    @Test
    void 결제_서버가_실패면_거래를_취소하고_상품_잠금을_푼다() {
        // Given
        Fixture f = stuckBuyNowTrade("IGN-failed");
        given(paymentClient.getPayment("IGN-failed")).willReturn(result(f.tradeId, "IGN-failed", "FAILED"));

        // When
        tradeResolveJob.resolve();

        // Then
        assertThat(tradeStatusOf(f.tradeId)).isEqualTo(TradeStatus.CANCELED);
        assertThat(itemStatusOf(f.itemId)).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void 승인_요청이_도착하지_않았으면_결제_대기로_되돌린다() {
        // Given
        Fixture f = stuckBuyNowTrade("IGN-ready");
        given(paymentClient.getPayment("IGN-ready")).willReturn(result(f.tradeId, "IGN-ready", "READY"));

        // When
        tradeResolveJob.resolve();

        // Then
        assertThat(tradeStatusOf(f.tradeId)).isEqualTo(TradeStatus.PAYMENT_PENDING);
        assertThat(itemStatusOf(f.itemId)).isEqualTo(ItemStatus.ACTIVE);
    }

    @Test
    void 결제_서버도_결과를_모르면_그대로_둔다() {
        // Given
        Fixture f = stuckBuyNowTrade("IGN-confirming");
        given(paymentClient.getPayment("IGN-confirming")).willReturn(result(f.tradeId, "IGN-confirming", "CONFIRMING"));

        // When
        tradeResolveJob.resolve();

        // Then
        assertThat(tradeStatusOf(f.tradeId)).isEqualTo(TradeStatus.CONFIRMING);
        assertThat(itemStatusOf(f.itemId)).isEqualTo(ItemStatus.BUY_NOW_PENDING);
    }

    @Test
    void 승인을_시작한_지_10분이_안된_거래는_조회하지_않는다() {
        // Given
        Fixture f = buyNowTrade("first@test.com", "IGN-recent");
        backdateConfirmStart(f.tradeId, 5);

        // When
        tradeResolveJob.resolve();

        // Then
        verify(paymentClient, never()).getPayment(anyString());
        assertThat(tradeStatusOf(f.tradeId)).isEqualTo(TradeStatus.CONFIRMING);
    }

    @Test
    void 한_건의_조회가_실패해도_나머지_거래는_처리한다() {
        // Given
        Fixture broken = stuckBuyNowTrade("IGN-broken");
        Fixture done = stuckBuyNowTrade("second@test.com", "IGN-next");
        given(paymentClient.getPayment("IGN-broken")).willThrow(new ResourceAccessException("timeout"));
        given(paymentClient.getPayment("IGN-next")).willReturn(result(done.tradeId, "IGN-next", "DONE"));

        // When
        tradeResolveJob.resolve();

        // Then
        assertThat(tradeStatusOf(broken.tradeId)).isEqualTo(TradeStatus.CONFIRMING);
        assertThat(tradeStatusOf(done.tradeId)).isEqualTo(TradeStatus.PAID);
    }

    private record Fixture(Long tradeId, Long itemId) {
    }

    // 승인을 시작한 지 11분 지난 즉시구매 거래
    private Fixture stuckBuyNowTrade(String orderId) {
        return stuckBuyNowTrade("buyer@test.com", orderId);
    }

    private Fixture stuckBuyNowTrade(String buyerEmail, String orderId) {
        Fixture f = buyNowTrade(buyerEmail, orderId);
        backdateConfirmStart(f.tradeId, 11);
        return f;
    }

    private Fixture buyNowTrade(String buyerEmail, String orderId) {
        String prefix = buyerEmail.substring(0, buyerEmail.indexOf('@'));
        User seller = userRepository.save(newUser(prefix + "-seller@test.com", prefix + "-seller"));
        User buyer = userRepository.save(newUser(buyerEmail, prefix));
        Item item = itemRepository.save(newItem(seller));

        Long tradeId = itemCommandService.buyNowItem(
                item.getId(), buyer.getId(), new ItemBuyNowRequest(item.getBuyNowPrice())).tradeId();
        tradePaymentService.startConfirm(tradeId, buyer.getId(), orderId);

        return new Fixture(tradeId, item.getId());
    }

    private void backdateConfirmStart(Long tradeId, int minutesAgo) {
        jdbcTemplate.update("UPDATE trades SET confirm_started_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutesAgo), tradeId);
    }

    private TradeStatus tradeStatusOf(Long tradeId) {
        return tradeRepository.findById(tradeId).orElseThrow().getStatus();
    }

    private ItemStatus itemStatusOf(Long itemId) {
        return itemRepository.findById(itemId).orElseThrow().getStatus();
    }

    private PaymentResult result(Long tradeId, String orderId, String status) {
        LocalDateTime approvedAt = "DONE".equals(status) ? LocalDateTime.now() : null;
        return new PaymentResult(tradeId, orderId, status, 1_000_000L, approvedAt, null, null);
    }
}
