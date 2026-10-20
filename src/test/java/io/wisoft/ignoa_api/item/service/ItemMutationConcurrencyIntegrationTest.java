package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.auction.service.AuctionService;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 서비스와 MySQL 트랜잭션으로 상품 수정·삭제·연장 경합 검증. */
@Import(ItemMutationConcurrencyIntegrationTest.Config.class)
class ItemMutationConcurrencyIntegrationTest extends IntegrationTestSupport {
    @MockitoBean AuctionCloseScheduler scheduler;
    @Autowired ItemCommandService commands;
    @Autowired AuctionService auctions;
    @Autowired ItemRepository items;
    @Autowired ItemMediaRepository media;
    @Autowired BidRepository bids;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Gate gate;
    @PersistenceContext EntityManager em;

    User seller;
    Item item;
    LocalDateTime endAt;

    @BeforeEach
    void setUp() {
        seller = users.save(newUser("mutation@race.test", "판매자"));
        endAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MICROS);
        item = items.save(newItem(seller, endAt));
        media.save(ItemMedia.from(item, "mutation/image.jpg", ItemMediaType.IMAGE));
        wishes.save(Wish.create(seller, item));
    }

    @AfterEach
    void tearDown() {
        gate.reset();
        jdbc.update("DELETE FROM outbox WHERE aggregate_type = 'ITEM' AND aggregate_id = ?", item.getId().toString());
        bids.deleteAllInBatch();
        wishes.deleteAllInBatch();
        media.deleteAllInBatch();
        jdbc.update("DELETE FROM items");
        users.deleteAllInBatch();
    }

    @Test
    void 상품_삭제시_미디어와_찜을_정리하고_파일_삭제_Outbox를_등록한다() {
        delete();
        assertDeleted();
        assertThat(jdbc.queryForList("SELECT payload FROM outbox WHERE aggregate_type = 'ITEM' " +
                        "AND aggregate_id = ? AND event_type = 'DELETE_ITEM_MEDIA'", String.class, item.getId().toString()))
                .containsExactly("mutation/image.jpg");
    }

    @Test
    void 미디어_추가_후_대기하던_삭제가_상품과_미디어를_정리한다() throws Exception {
        Pair pair = holdWriteThen(
                () -> changeMedia(List.of(), new UploadedMedia("mutation/new.jpg", ItemMediaType.IMAGE)),
                "findForUpdateById", this::delete);
        assertSuccess(pair);
        assertDeleted();
        assertThat(jdbc.queryForList("SELECT payload FROM outbox WHERE aggregate_type = 'ITEM' " +
                        "AND aggregate_id = ? AND event_type = 'DELETE_ITEM_MEDIA'", String.class, item.getId().toString()))
                .containsExactlyInAnyOrder("mutation/image.jpg", "mutation/new.jpg");
    }

    @Test
    void 동시_미디어_삭제에서도_이미지는_최소_한장_유지된다() throws Exception {
        ItemMedia secondImage = media.save(ItemMedia.from(item, "mutation/second.jpg", ItemMediaType.IMAGE));
        Long firstImageId = media.findAllByItemId(item.getId()).stream()
                .map(ItemMedia::getId).filter(id -> !id.equals(secondImage.getId())).findFirst().orElseThrow();
        Pair pair = holdWriteThen(() -> changeMedia(List.of(firstImageId)),
                "findForUpdateById", () -> changeMedia(List.of(secondImage.getId())));
        assertThat(pair.first.error).isNull();
        assertBusinessError(pair.second, ErrorCode.ITEM_IMAGE_REQUIRED);
        assertThat(media.findAllByItemId(item.getId())).extracting(ItemMedia::getId).containsExactly(secondImage.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_type = 'ITEM' " +
                        "AND aggregate_id = ?", Long.class, item.getId().toString())).isEqualTo(1);
    }

    @Test
    void 동시_미디어_추가에서도_동영상은_한개만_반영된다() throws Exception {
        Pair pair = holdWriteThen(
                () -> changeMedia(List.of(), new UploadedMedia("mutation/first.mp4", ItemMediaType.VIDEO)),
                "findForUpdateById",
                () -> changeMedia(List.of(), new UploadedMedia("mutation/second.mp4", ItemMediaType.VIDEO)));
        assertThat(pair.first.error).isNull();
        assertBusinessError(pair.second, ErrorCode.ITEM_VIDEO_LIMIT_EXCEEDED);
        assertThat(media.findAllByItemId(item.getId())).extracting(ItemMedia::getMediaType)
                .containsExactlyInAnyOrder(ItemMediaType.IMAGE, ItemMediaType.VIDEO);
    }

    @Test
    void 수정이_먼저_행을_갱신하면_삭제는_커밋_후_정상_처리된다() throws Exception {
        Pair pair = holdWriteThen(this::update, "findForUpdateById", this::delete);
        assertSuccess(pair);
        assertDeleted();
        assertThat(title()).isEqualTo("변경된 제목");
    }

    @Test
    void 수정이_먼저_갱신해도_연장과_수정이_모두_보존된다() throws Exception {
        Pair pair = holdWriteThen(this::update, "extendIfActive", this::extend);
        assertSuccess(pair);
        assertUpdatedAndExtended(1);
    }

    @Test
    void 연장이_먼저_갱신하면_수정은_커밋_후_진행된다() throws Exception {
        Pair pair = holdWriteThen(this::extend, "findForUpdateById", this::update);
        assertSuccess(pair);
        assertUpdatedAndExtended(1);
    }

    @Test
    void 삭제가_먼저_갱신하면_삭제_전_상태를_읽은_연장도_거절된다() throws Exception {
        Pair pair = holdWriteThen(this::delete, "extendIfActive", this::extend);
        assertThat(pair.first.error).isNull();
        assertBusinessError(pair.second, ErrorCode.AUCTION_EXTEND_CONFLICT);
        assertDeleted();
        assertExtension(0);
    }

    @Test
    void 연장이_먼저_갱신해도_입찰없는_상품은_삭제할_수_있다() throws Exception {
        Pair pair = holdWriteThen(this::extend, "findForUpdateById", this::delete);
        assertSuccess(pair);
        assertDeleted();
        assertExtension(1);
    }

    @Test
    void 동시_수정은_순서대로_반영된다() throws Exception {
        Pair pair = holdWriteThen(this::update, "findForUpdateById", () -> commands.updateItem(item.getId(), seller.getId(),
                new ItemUpdateRequest("두번째 제목", null, null, null, null, null, List.of()), List.of()));
        assertSuccess(pair);
        assertThat(title()).isEqualTo("두번째 제목");
        assertThat(media.count()).isEqualTo(1);
    }

    @Test
    void 동시_삭제는_한번만_성공하고_후속_요청은_없는_상품으로_거절된다() throws Exception {
        Pair pair = holdWriteThen(this::delete, "findForUpdateById", this::delete);
        assertThat(pair.first.error).isNull();
        assertBusinessError(pair.second, ErrorCode.ITEM_NOT_FOUND);
        assertDeleted();
    }

    @Test
    void 동시에_열번_연장해도_최대_세번만_반영된다() throws Exception {
        List<Outcome> results = concurrentExtensions();
        assertThat(results.stream().filter(r -> r.error == null)).hasSize(3);
        assertThat(results.stream().filter(r -> r.error != null)).hasSize(7)
                .allSatisfy(r -> assertBusinessError(r, ErrorCode.AUCTION_EXTEND_CONFLICT));
        assertExtension(3);
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(media.count()).isEqualTo(1);
    }

    @Test
    void 삭제가_먼저_갱신하면_대기하던_수정은_상품을_찾지_못하고_거절된다() throws Exception {
        Pair pair = holdWriteThen(this::delete, "findForUpdateById", this::update);

        assertThat(pair.first.error).isNull();
        assertBusinessError(pair.second, ErrorCode.ITEM_NOT_FOUND);
        assertDeleted();
        assertThat(title()).isEqualTo("테스트 상품");
    }

    private List<Outcome> concurrentExtensions() throws Exception {
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<Outcome>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < 10; i++) {
                    futures.add(executor.submit(() -> outcome(() -> {
                        ready.countDown();
                        await(start);
                        return extend();
                    })));
                }
                await(ready);
            } finally {
                start.countDown();
            }
            List<Outcome> results = new ArrayList<>();
            for (Future<Outcome> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        }
    }

    // 첫 실제 DB 쓰기를 flush하고 커밋 전에 다른 작업을 SQL/검증 경계까지 진입시킨다.
    private Pair holdWriteThen(Callable<?> first, String secondBoundary, Callable<?> second) throws Exception {
        gate.arm(secondBoundary);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstFuture = executor.submit(() -> outcome(() ->
                    new TransactionTemplate(transactionManager).execute(tx -> {
                        try {
                            Object value = first.call();
                            em.flush();
                            gate.reached.countDown();
                            await(gate.release);
                            return value;
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })));
            Future<Outcome> secondFuture;
            try {
                await(gate.reached);
                secondFuture = executor.submit(() -> {
                    gate.secondThread = Thread.currentThread();
                    return outcome(second);
                });
                await(gate.entered);
                if (secondBoundary.equals("findForUpdateById")) {
                    assertThatThrownBy(() -> secondFuture.get(100, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                }
            } finally {
                gate.release.countDown();
            }
            return new Pair(firstFuture.get(10, TimeUnit.SECONDS), secondFuture.get(10, TimeUnit.SECONDS));
        } finally {
            gate.reset();
        }
    }

    private Object update() {
        return commands.updateItem(item.getId(), seller.getId(),
                new ItemUpdateRequest("변경된 제목", null, null, null, null, null, List.of()), List.of());
    }
    private Object changeMedia(List<Long> deleteIds, UploadedMedia... uploads) {
        return commands.updateItem(item.getId(), seller.getId(),
                new ItemUpdateRequest(null, null, null, null, null, null, deleteIds), List.of(uploads));
    }
    private Object delete() { return commands.deleteItem(item.getId(), seller.getId()); }
    private Object extend() { return auctions.extendAuction(item.getId(), seller.getId()); }
    private String title() { return jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, item.getId()); }
    private String status() { return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, item.getId()); }

    private void assertDeleted() {
        assertThat(status()).isEqualTo("DELETED");
        assertThat(items.findById(item.getId())).isEmpty();
        assertThat(media.count()).isZero();
        assertThat(wishes.count()).isZero();
        assertThat(bids.count()).isZero();
    }
    private void assertUpdatedAndExtended(int extensions) {
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(title()).isEqualTo("변경된 제목");
        assertThat(media.count()).isEqualTo(1);
        assertExtension(extensions);
    }
    private void assertExtension(int extensions) {
        assertThat(jdbc.queryForObject("SELECT extension_count FROM items WHERE id = ?", Integer.class, item.getId()))
                .isEqualTo(extensions);
        assertThat(jdbc.queryForObject("SELECT end_at FROM items WHERE id = ?", LocalDateTime.class, item.getId()))
                .isEqualTo(endAt.plusDays(extensions));
    }
    private static void assertBusinessError(Outcome result, ErrorCode expected) {
        assertThat(result.error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result.error).getErrorCode()).isEqualTo(expected);
    }
    private static void assertSuccess(Pair pair) {
        assertThat(pair.first.error).isNull();
        assertThat(pair.second.error).isNull();
    }
    private static Outcome outcome(Callable<?> task) {
        try { return new Outcome(task.call(), null); }
        catch (Throwable error) { return new Outcome(null, error); }
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("경합 경계 도달").isTrue();
    }
    record Outcome(Object value, Throwable error) {}
    record Pair(Outcome first, Outcome second) {}

    @TestConfiguration
    static class Config {
        @Bean Gate mutationGate() { return new Gate(); }
    }
    @Aspect
    static class Gate {
        volatile Thread secondThread;
        volatile String secondBoundary;
        CountDownLatch reached;
        CountDownLatch entered;
        CountDownLatch release;

        void arm(String boundary) {
            reached = new CountDownLatch(1);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            secondBoundary = boundary;
        }
        void reset() {
            secondThread = null;
            secondBoundary = null;
        }
        @Around("execution(* io.wisoft.ignoa_api.item.repository.ItemCommandRepository.*(..)) || " +
                "execution(* io.wisoft.ignoa_api.item.repository.ItemRepository.findForUpdateById(..))")
        Object intercept(ProceedingJoinPoint point) throws Throwable {
            String method = point.getSignature().getName();
            if (Thread.currentThread() == secondThread && method.equals(secondBoundary)) entered.countDown();
            return point.proceed();
        }
    }
}
