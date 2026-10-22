package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemCommandService;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.payment.dto.PaymentResult;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.trade.service.PaymentResultApplier;
import io.wisoft.ignoa_api.trade.service.TradePaymentService;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 서비스, 트랜잭션, MySQL UPDATE를 사용한다. 테스트 전용 Aspect는 SQL의 now 인자와
 * 실행 순서만 제어한다. 입찰/연장/예약은 종료 1µs 전, 마감은 종료 시각에 실행한 것으로 재현한다.
 * 첫 UPDATE가 성공한 뒤 커밋하기 전에 다른 작업을 진입시켜, 단순 동시 시작으로 놓치는 순서를 검증한다.
 */
@Import(AuctionCloseConcurrencyIntegrationTest.ConcurrencyConfig.class)
class AuctionCloseConcurrencyIntegrationTest extends IntegrationTestSupport {

    @MockitoBean AuctionCloseScheduler scheduler;
    @Autowired AuctionFacade auctionFacade;
    @Autowired AuctionService auctionService;
    @Autowired BidService bidService;
    @Autowired ItemCommandService itemCommandService;
    @Autowired TradePaymentService paymentService;
    @Autowired PaymentResultApplier paymentResultApplier;
    @Autowired ItemRepository itemRepository;
    @Autowired ItemMediaRepository mediaRepository;
    @Autowired UserRepository userRepository;
    @Autowired BidRepository bidRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired TradeRepository tradeRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Interleaving interleaving;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    private User seller;
    private User bidder;
    private User buyer;
    private Item item;
    private LocalDateTime endAt;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(newUser("seller@race.test", "판매자"));
        bidder = userRepository.save(newUser("bidder@race.test", "입찰자"));
        buyer = userRepository.save(newUser("buyer@race.test", "구매자"));
        endAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MICROS);
        item = itemRepository.save(newItem(seller, endAt));
        interleaving.endAt = endAt;
    }

    @AfterEach
    void tearDown() {
        interleaving.reset();
        tradeRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        bidRepository.deleteAllInBatch();
        mediaRepository.deleteAllInBatch();
        // SQLRestriction이 붙은 벌크 삭제는 DELETED 행을 남기므로 물리 삭제한다.
        jdbc.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 종료_직전_입찰이_먼저_갱신하면_마감은_그_입찰의_커밋까지_기다려_낙찰을_확정한다() throws Exception {
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.second.value).isEqualTo(true);
        assertThat(results.second.error).isNull();
        assertAuctionWinner();
    }

    @Test
    void 마감이_먼저_갱신하면_종료_직전_시각을_가진_입찰도_거절한다() throws Exception {
        Results results = race("closeIfExpired", this::close,
                "raiseCurrentPriceIfHigher", this::bid);

        assertClosedFirst(results, ErrorCode.BID_CONFLICT);
        assertNoBidClosed();
    }

    @Test
    void 종료_직전_연장이_먼저_갱신하면_이미_조회한_마감_대상도_마감하지_않는다() throws Exception {
        Results results = race("extendIfActive", this::extend,
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(false);
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT end_at FROM items WHERE id = ?", LocalDateTime.class, item.getId()))
                .isEqualTo(endAt.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT extension_count FROM items WHERE id = ?", Integer.class, item.getId()))
                .isEqualTo(1);
        assertNoSettlement();
    }

    @Test
    void 마감이_먼저_갱신하면_종료_직전_시각을_가진_연장도_거절한다() throws Exception {
        Results results = race("closeIfExpired", this::close,
                "extendIfActive", this::extend);

        assertClosedFirst(results, ErrorCode.AUCTION_EXTEND_CONFLICT);
        assertNoBidClosed();
        assertThat(jdbc.queryForObject("SELECT extension_count FROM items WHERE id = ?", Integer.class, item.getId()))
                .isZero();
    }

    @Test
    void 입찰_없는_상품의_삭제가_먼저_갱신하면_마감을_생략한다() throws Exception {
        Results results = race("softDeleteIfActive", this::delete,
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(false);
        assertThat(status()).isEqualTo("DELETED");
        assertNoSettlement();
    }

    @Test
    void 마감이_먼저_갱신하면_상품_삭제를_거절한다() throws Exception {
        Results results = race("closeIfExpired", this::close,
                "findForUpdateById", this::delete);

        assertClosedFirst(results, ErrorCode.ITEM_DELETE_CONFLICT);
        assertNoBidClosed();
    }

    @Test
    void 즉시구매_예약이_먼저_갱신하면_종료_시각에도_대기_상태를_마감하지_않는다() throws Exception {
        Long tradeId = buyNowTrade();
        Results results = race("reserveBuyNowIfActive", () -> reserve(tradeId),
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(false);
        assertThat(status()).isEqualTo("BUY_NOW_RESERVED");
        assertTradeStatus(tradeId, "PAYMENT_CONFIRMING");
        assertNoSettlement();
    }

    @Test
    void 마감이_먼저_갱신하면_즉시구매_예약을_거절하고_거래_승인_시작도_롤백한다() throws Exception {
        Long tradeId = buyNowTrade();
        Results results = race("closeIfExpired", this::close,
                "reserveBuyNowIfActive", () -> reserve(tradeId));

        assertClosedFirst(results, ErrorCode.BUY_NOW_CONFLICT);
        assertNoBidClosed();
        assertTradeStatus(tradeId, "PAYMENT_PENDING");
        assertThat(jdbc.queryForObject("SELECT confirming_order_id FROM trades WHERE id = ?", String.class, tradeId))
                .isNull();
    }

    @Test
    void 즉시구매_결제_완료와_마감이_겹쳐도_경매_낙찰을_만들지_않는다() throws Exception {
        bid();
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("completeBuyNowIfPending", () -> paymentResultApplier.apply(tradeId,
                        new PaymentResult(tradeId, "race-order", "DONE", 1_000_000L, endAt, null, null)),
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(true);
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(false);
        assertThat(status()).isEqualTo("BUY_NOW_SOLD");
        assertTradeStatus(tradeId, "PAID");
        assertThat(jdbc.queryForObject("SELECT highest_bidder_id FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(buyer.getId());
        assertThat(jdbc.queryForList("SELECT status FROM bids WHERE item_id = ?", String.class, item.getId()))
                .containsExactly("LOST");
        assertThat(count("trades", "type = 'AUCTION'")).isZero();
        assertThat(count("chat_rooms", "buyer_id = " + buyer.getId())).isEqualTo(1);
    }

    @Test
    void 대기_상태에서_마감이_먼저_진입해도_결제_완료는_정상_반영된다() throws Exception {
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("closeIfExpired", this::close,
                "completeBuyNowIfPending", () -> paymentResultApplier.apply(tradeId,
                        new PaymentResult(tradeId, "race-order", "DONE", 1_000_000L, endAt, null, null)));

        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(false);
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(true);
        assertThat(status()).isEqualTo("BUY_NOW_SOLD");
        assertTradeStatus(tradeId, "PAID");
        assertThat(count("trades", "type = 'AUCTION'")).isZero();
        assertThat(count("chat_rooms", "buyer_id = " + buyer.getId())).isEqualTo(1);
    }

    @Test
    void 만료_후_즉시구매_취소가_먼저_갱신하면_복원된_상품을_낙찰_처리한다() throws Exception {
        bid();
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("cancelBuyNowIfPending", () -> paymentResultApplier.apply(tradeId,
                        new PaymentResult(tradeId, "race-order", "FAILED", 1_000_000L, null, "FAILED", "실패")),
                "closeIfExpired", this::close);

        assertThat(results.first.error).isNull();
        assertThat(results.second.error).isNull();
        assertThat(results.second.value).isEqualTo(true);
        assertTradeStatus(tradeId, "CANCELED");
        assertAuctionWinner();
    }

    @Test
    void 대기_상태에서_마감을_생략한_뒤_예약이_취소되면_다음_실행에서_마감한다() throws Exception {
        bid();
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("closeIfExpired", this::close,
                "cancelBuyNowIfPending", () -> paymentResultApplier.apply(tradeId,
                        new PaymentResult(tradeId, "race-order", "FAILED", 1_000_000L, null, "FAILED", "실패")));

        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(false);
        assertThat(results.second.error).isNull();
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(close()).isTrue();
        assertTradeStatus(tradeId, "CANCELED");
        assertAuctionWinner();
    }

    @Test
    void 수정이_먼저_행을_갱신하면_마감도_커밋_후_정상_처리한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        interleaving.arm("unused", "closeIfExpired");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> update = executor.submit(() -> outcome(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        itemCommandService.updateItem(item.getId(), seller.getId(),
                                new ItemUpdateRequest("변경된 제목", null, null, null, null, null, List.of()), List.of());
                        entityManager.flush();
                        interleaving.firstReached.countDown();
                        try { await(interleaving.release); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        return null;
                    })));
            Future<Boolean> close;
            try {
                await(interleaving.firstReached);
                close = executor.submit(this::close);
                await(interleaving.secondEntered);
            } finally {
                interleaving.release.countDown();
            }
            assertThat(update.get(10, TimeUnit.SECONDS).error).isNull();
            assertThat(close.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            interleaving.disarm();
        }
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("변경된 제목");
        assertNoBidClosed();
    }

    @Test
    void 입찰_UPDATE_후_기록_저장_전에_삭제가_진입해도_입찰_커밋_후_삭제를_거절한다() throws Exception {
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "findForUpdateById", this::delete);

        assertThat(results.first.error).isNull();
        assertError(results.second, ErrorCode.ITEM_DELETE_CONFLICT);
        assertActiveBid();
        assertNoSettlement();
    }

    @Test
    void 삭제가_먼저_갱신하면_삭제_전_상품을_읽은_입찰도_반영되지_않는다() throws Exception {
        Results results = race("softDeleteIfActive", this::delete,
                "raiseCurrentPriceIfHigher", this::bid);

        assertThat(results.first.error).isNull();
        assertError(results.second, ErrorCode.BID_CONFLICT);
        assertThat(status()).isEqualTo("DELETED");
        assertThat(count("bids", "1 = 1")).isZero();
        assertThat(currentPrice()).isEqualTo(1_000L);
        assertThat(highestBidderId()).isNull();
        assertNoSettlement();
    }

    @Test
    void 입찰이_먼저_갱신해도_연장은_입찰_가격과_최고입찰자를_보존한다() throws Exception {
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "extendIfActive", this::extend);

        assertBothSucceeded(results);
        assertActiveBid();
        assertExtended();
    }

    @Test
    void 연장이_먼저_갱신해도_입찰은_연장된_종료시각을_보존한다() throws Exception {
        Results results = race("extendIfActive", this::extend,
                "raiseCurrentPriceIfHigher", this::bid);

        assertBothSucceeded(results);
        assertActiveBid();
        assertExtended();
    }

    @Test
    void 입찰이_먼저_갱신해도_즉시구매_예약은_기존_입찰을_보존한다() throws Exception {
        Long tradeId = buyNowTrade();
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "reserveBuyNowIfActive", () -> reserve(tradeId));

        assertBothSucceeded(results);
        assertThat(status()).isEqualTo("BUY_NOW_RESERVED");
        assertBidPriceAndRecord();
        assertTradeStatus(tradeId, "PAYMENT_CONFIRMING");
    }

    @Test
    void 즉시구매_예약이_먼저_갱신해도_결제대기_중_입찰은_허용한다() throws Exception {
        Long tradeId = buyNowTrade();
        Results results = race("reserveBuyNowIfActive", () -> reserve(tradeId),
                "raiseCurrentPriceIfHigher", this::bid);

        assertBothSucceeded(results);
        assertThat(status()).isEqualTo("BUY_NOW_RESERVED");
        assertBidPriceAndRecord();
        assertTradeStatus(tradeId, "PAYMENT_CONFIRMING");
    }

    @Test
    void 입찰이_먼저_갱신하면_즉시구매_결제완료가_입찰을_패찰처리하고_거래가를_확정한다() throws Exception {
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "completeBuyNowIfPending", () -> completePayment(tradeId));

        assertBothSucceeded(results);
        assertBuyNowCompleted(tradeId);
        assertThat(jdbc.queryForList("SELECT status FROM bids WHERE item_id = ?", String.class, item.getId()))
                .containsExactly("LOST");
        assertThat(count("bids", "price = 2000")).isEqualTo(1);
    }

    @Test
    void 즉시구매_결제완료가_먼저_갱신하면_입찰은_거절되고_추가_입찰기록이_없다() throws Exception {
        Long tradeId = buyNowTrade();
        reserve(tradeId);
        Results results = race("completeBuyNowIfPending", () -> completePayment(tradeId),
                "raiseCurrentPriceIfHigher", this::bid);

        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(true);
        assertError(results.second, ErrorCode.BID_CONFLICT);
        assertBuyNowCompleted(tradeId);
        assertThat(count("bids", "1 = 1")).isZero();
    }

    @Test
    void 수정이_먼저_갱신하면_대기하던_입찰은_변경된_즉시구매가_조건으로_거절한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        interleaving.arm("unused", "raiseCurrentPriceIfHigher");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> update = executor.submit(() -> outcome(() ->
                    new TransactionTemplate(transactionManager).execute(tx -> {
                        itemCommandService.updateItem(item.getId(), seller.getId(),
                                new ItemUpdateRequest("변경된 제목", null, null, null, null, 1_500L, List.of()), List.of());
                        entityManager.flush();
                        interleaving.firstReached.countDown();
                        try { await(interleaving.release); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        return null;
                    })));
            Future<Outcome> bid;
            try {
                await(interleaving.firstReached);
                bid = executor.submit(() -> outcome(this::bid));
                await(interleaving.secondEntered);
            } finally {
                interleaving.release.countDown();
            }
            assertThat(update.get(10, TimeUnit.SECONDS).error).isNull();
            assertError(bid.get(10, TimeUnit.SECONDS), ErrorCode.BID_CONFLICT);
        } finally {
            interleaving.disarm();
        }
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(currentPrice()).isEqualTo(1_000L);
        assertThat(highestBidderId()).isNull();
        assertThat(count("bids", "1 = 1")).isZero();
        assertThat(jdbc.queryForObject("SELECT buy_now_price FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(1_500L);
    }

    @Test
    void 입찰이_먼저_갱신하면_수정은_커밋을_기다린_뒤_입찰가와_입찰자를_보존한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "findForUpdateById", () -> itemCommandService.updateItem(item.getId(), seller.getId(),
                        new ItemUpdateRequest("변경된 제목", null, null, null, null, null, List.of()), List.of()));

        assertBothSucceeded(results);
        assertActiveBid();
        assertNoSettlement();
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("변경된 제목");
        assertThat(jdbc.queryForObject("SELECT buy_now_price FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(1_000_000L);
    }

    @Test
    void 입찰이_먼저_갱신하면_대기하던_즉시구매가_수정은_입찰_내역을_확인하고_거절한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        Results results = race("raiseCurrentPriceIfHigher", this::bid,
                "findForUpdateById", () -> itemCommandService.updateItem(item.getId(), seller.getId(),
                        new ItemUpdateRequest("변경된 제목", null, null, null, null, 2_000_000L, List.of()), List.of()));

        assertThat(results.first.error).isNull();
        assertError(results.second, ErrorCode.BUY_NOW_PRICE_CHANGED_NOT_ALLOWED);
        assertActiveBid();
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("테스트 상품");
        assertThat(jdbc.queryForObject("SELECT buy_now_price FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(1_000_000L);
        assertThat(mediaRepository.count()).isEqualTo(1);
    }

    @Test
    void 마감이_먼저_갱신하면_대기하던_수정은_마감_상태를_확인하고_거절한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        Results results = race("closeIfExpired", this::close,
                "findForUpdateById", () -> itemCommandService.updateItem(item.getId(), seller.getId(),
                        new ItemUpdateRequest("변경된 제목", null, null, null, null, null, List.of()), List.of()));

        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(true);
        assertError(results.second, ErrorCode.AUCTION_ALREADY_CLOSED);
        assertNoBidClosed();
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("테스트 상품");
    }

    @Test
    void 즉시구매_예약이_먼저_갱신하면_대기하던_수정은_결제_대기_상태를_확인하고_거절한다() throws Exception {
        mediaRepository.save(ItemMedia.from(item, "race/image.jpg", ItemMediaType.IMAGE));
        Long tradeId = buyNowTrade();
        Results results = race("reserveBuyNowIfActive", () -> reserve(tradeId),
                "findForUpdateById", () -> itemCommandService.updateItem(item.getId(), seller.getId(),
                        new ItemUpdateRequest("변경된 제목", null, null, null, null, null, List.of()), List.of()));

        assertThat(results.first.error).isNull();
        assertError(results.second, ErrorCode.AUCTION_ALREADY_CLOSED);
        assertThat(status()).isEqualTo("BUY_NOW_RESERVED");
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("테스트 상품");
    }

    private boolean completePayment(Long tradeId) {
        return paymentResultApplier.apply(tradeId,
                new PaymentResult(tradeId, "race-order", "DONE", 1_000_000L, endAt, null, null));
    }

    private void assertBothSucceeded(Results results) {
        assertThat(results.first.error).isNull();
        assertThat(results.second.error).isNull();
    }

    private long currentPrice() {
        return jdbc.queryForObject("SELECT current_price FROM items WHERE id = ?", Long.class, item.getId());
    }

    private Long highestBidderId() {
        return jdbc.queryForObject("SELECT highest_bidder_id FROM items WHERE id = ?", Long.class, item.getId());
    }

    private void assertActiveBid() {
        assertThat(status()).isEqualTo("ACTIVE");
        assertBidPriceAndRecord();
    }

    private void assertBidPriceAndRecord() {
        assertThat(currentPrice()).isEqualTo(2_000L);
        assertThat(highestBidderId()).isEqualTo(bidder.getId());
        assertThat(count("bids", "1 = 1")).isEqualTo(1);
        assertThat(count("bids", "price = 2000 AND bidder_id = " + bidder.getId())).isEqualTo(1);
    }

    private void assertExtended() {
        assertThat(jdbc.queryForObject("SELECT end_at FROM items WHERE id = ?", LocalDateTime.class, item.getId()))
                .isEqualTo(endAt.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT extension_count FROM items WHERE id = ?", Integer.class, item.getId()))
                .isEqualTo(1);
    }

    private void assertBuyNowCompleted(Long tradeId) {
        assertThat(status()).isEqualTo("BUY_NOW_SOLD");
        assertThat(currentPrice()).isEqualTo(1_000_000L);
        assertThat(highestBidderId()).isEqualTo(buyer.getId());
        assertTradeStatus(tradeId, "PAID");
        assertThat(count("trades", "type = 'AUCTION'")).isZero();
        assertThat(count("chat_rooms", "buyer_id = " + buyer.getId())).isEqualTo(1);
    }

    private Results race(String firstMethod, Callable<?> first, String secondMethod, Callable<?> second) throws Exception {
        interleaving.arm(firstMethod, secondMethod);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstFuture = executor.submit(() -> outcome(first));
            Future<Outcome> secondFuture;
            try {
                await(interleaving.firstReached);
                secondFuture = executor.submit(() -> outcome(second));
                await(interleaving.secondEntered);
                if (secondMethod.equals("findForUpdateById")) {
                    assertThatThrownBy(() -> secondFuture.get(100, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                }
            } finally {
                interleaving.release.countDown();
            }
            return new Results(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
        } finally {
            interleaving.disarm();
        }
    }

    private Object bid() {
        return bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(2_000L));
    }

    private boolean close() { return auctionFacade.closeAuction(item.getId()); }
    private Object extend() { return auctionService.extendAuction(item.getId(), seller.getId()); }
    private Object delete() { return itemCommandService.deleteItem(item.getId(), seller.getId()); }
    private Long buyNowTrade() {
        return itemCommandService.buyNowItem(item.getId(), buyer.getId(), new ItemBuyNowRequest(1_000_000L)).tradeId();
    }
    private Object reserve(Long tradeId) {
        paymentService.startConfirm(tradeId, buyer.getId(), "race-order");
        return null;
    }

    private String status() {
        // DELETED는 엔티티의 SQLRestriction으로 조회되지 않아 JDBC로 최종 DB 상태를 확인한다.
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, item.getId());
    }

    private long count(String table, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE item_id = ? AND " + condition,
                Long.class, item.getId());
    }

    private void assertNoSettlement() {
        assertThat(count("trades", "type = 'AUCTION'")).isZero();
        assertThat(count("chat_rooms", "1 = 1")).isZero();
    }

    private void assertNoBidClosed() {
        assertThat(status()).isEqualTo("AUCTION_UNSOLD");
        assertThat(bidRepository.count()).isZero();
        assertNoSettlement();
    }

    private void assertAuctionWinner() {
        assertThat(status()).isEqualTo("AUCTION_AWARDED");
        assertThat(jdbc.queryForObject("SELECT current_price FROM items WHERE id = ?", Long.class, item.getId())).isEqualTo(2_000L);
        assertThat(jdbc.queryForObject("SELECT highest_bidder_id FROM items WHERE id = ?", Long.class, item.getId()))
                .isEqualTo(bidder.getId());
        assertThat(jdbc.queryForList("SELECT status FROM bids WHERE item_id = ?", String.class, item.getId())).containsExactly("WON");
        assertThat(count("trades", "type = 'AUCTION' AND status = 'PAYMENT_PENDING' AND amount = 2000 AND buyer_id = " + bidder.getId()))
                .isEqualTo(1);
        assertThat(count("trades", "type = 'AUCTION'")).isEqualTo(1);
        assertThat(count("chat_rooms", "buyer_id = " + bidder.getId())).isEqualTo(1);
        assertThat(count("chat_rooms", "1 = 1")).isEqualTo(1);
        assertThat(close()).isFalse();
    }

    private void assertTradeStatus(Long id, String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM trades WHERE id = ?", String.class, id)).isEqualTo(expected);
    }

    private void assertClosedFirst(Results results, ErrorCode error) {
        assertThat(results.first.error).isNull();
        assertThat(results.first.value).isEqualTo(true);
        assertError(results.second, error);
    }

    private static void assertError(Outcome outcome, ErrorCode code) {
        assertThat(outcome.error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) outcome.error).getErrorCode()).isEqualTo(code);
    }

    private static Outcome outcome(Callable<?> task) {
        try { return new Outcome(task.call(), null); }
        catch (Throwable error) { return new Outcome(null, error); }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("경합 지점 도달 제한 시간").isTrue();
    }

    private record Outcome(Object value, Throwable error) {}
    private record Results(Outcome first, Outcome second) {}

    @TestConfiguration
    static class ConcurrencyConfig {
        @Bean Interleaving interleaving() { return new Interleaving(); }
    }

    @Aspect
    static class Interleaving {
        volatile LocalDateTime endAt;
        volatile String firstMethod;
        volatile String secondMethod;
        CountDownLatch firstReached;
        CountDownLatch secondEntered;
        CountDownLatch release;

        void arm(String first, String second) {
            firstReached = new CountDownLatch(1);
            secondEntered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            firstMethod = first;
            secondMethod = second;
        }

        void disarm() { firstMethod = null; secondMethod = null; }
        void reset() { disarm(); endAt = null; }

        @Around("execution(* io.wisoft.ignoa_api.item.repository.ItemCommandRepository.*(..)) || " +
                "execution(* io.wisoft.ignoa_api.item.repository.ItemRepository.findForUpdateById(..)) || " +
                "execution(* io.wisoft.ignoa_api.item.service.ItemMediaService.validateMediaComposition(..))")
        Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
            String method = joinPoint.getSignature().getName();
            Object[] args = joinPoint.getArgs();
            // 반환값/SQL/상태 조건은 바꾸지 않고, 서비스가 전달한 시각만 경계 시각으로 고정한다.
            if (endAt != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof LocalDateTime) {
                        args[i] = method.equals("closeIfExpired") ? endAt : endAt.minusNanos(1_000);
                    }
                }
            }
            if (method.equals(secondMethod)) secondEntered.countDown();
            Object result = joinPoint.proceed(args);
            if (method.equals(firstMethod)) {
                firstReached.countDown();
                await(release);
            }
            return result;
        }
    }
}
