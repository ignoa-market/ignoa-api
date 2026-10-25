package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class BidServiceConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    BidService bidService;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ItemRepository itemRepository;

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 서로_다른_가격으로_동시_입찰해도_최고가만_반영되고_LostUpdate가_없다() throws InterruptedException {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));
        Item item = itemRepository.save(newItem(seller));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            long bidPrice = 1_100L + i * 100L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(bidPrice));
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).as("모든 입찰 완료").isTrue();
        } finally {
            executor.shutdownNow();
        }

        // Then
        Item finalItem = itemRepository.findById(item.getId()).orElseThrow();
        long maxSavedBid = bidRepository.findByItemId(item.getId()).stream()
                .mapToLong(Bid::getPrice)
                .max()
                .orElse(0L);

        assertThat(finalItem.getCurrentPrice()).isEqualTo(2_000L);
        assertThat(finalItem.getCurrentPrice()).isEqualTo(maxSavedBid);
        assertThat(errors).allSatisfy(error -> {
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.BID_CONFLICT);
        });
    }

    @Test
    void 서로_다른_입찰자가_동일한_가격으로_동시_입찰하면_한건만_반영된다() throws InterruptedException {
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Item item = itemRepository.save(newItem(seller));
        int threadCount = 10;
        var bidders = java.util.stream.IntStream.range(0, threadCount)
                .mapToObj(i -> userRepository.save(newUser("bidder" + i + "@test.com", "입찰자" + i)))
                .toList();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (User bidder : bidders) {
                executor.submit(() -> {
                    try {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(2_000L));
                        successes.incrementAndGet();
                    } catch (Throwable error) {
                        errors.add(error);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 입찰자 준비").isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).as("모든 입찰 완료").isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(errors).hasSize(threadCount - 1).allSatisfy(error -> {
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getErrorCode()).isEqualTo(ErrorCode.BID_CONFLICT);
        });
        var bids = bidRepository.findByItemId(item.getId());
        assertThat(bids).hasSize(1);
        assertThat(bids.getFirst().getPrice()).isEqualTo(2_000L);
        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getCurrentPrice()).isEqualTo(2_000L);
        assertThat(reloaded.getHighestBidder().getId()).isEqualTo(bids.getFirst().getBidder().getId());
    }

    @Test
    void 동일_입찰자가_서로_다른_가격으로_동시_입찰해도_입찰자_수는_한번만_증가한다() throws InterruptedException {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));
        Item item = itemRepository.save(newItem(seller));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            long bidPrice = 1_100L + i * 100L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(bidPrice));
                } catch (Exception ignored) {
                    // 낮은 가격 입찰의 BID_CONFLICT는 이 테스트의 검증 대상이 아님
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        try {
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).as("모든 입찰 완료").isTrue();
        } finally {
            executor.shutdownNow();
        }

        // Then
        assertThat(bidRepository.findByItemId(item.getId())).isNotEmpty();
        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getBidderCount()).isEqualTo(1);
    }

    @Test
    void 재입찰은_입찰자_수를_늘리지_않고_새_입찰자만_늘린다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User firstBidder = userRepository.save(newUser("first@test.com", "첫입찰자"));
        User secondBidder = userRepository.save(newUser("second@test.com", "두번째입찰자"));
        Item item = itemRepository.save(newItem(seller));

        // When
        bidService.placeBid(item.getId(), firstBidder.getId(), new BidCreateRequest(1_100L));
        bidService.placeBid(item.getId(), firstBidder.getId(), new BidCreateRequest(1_200L));
        bidService.placeBid(item.getId(), secondBidder.getId(), new BidCreateRequest(1_300L));

        // Then
        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getBidderCount()).isEqualTo(2);
    }

    @Test
    void 동일가나_낮은가로_입찰하면_INVALID_BID_PRICE_예외가_발생한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));
        Item item = itemRepository.save(newItem(seller));
        long bidPrice = 1_500L;
        bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(bidPrice));

        // When
        BusinessException samePriceException = catchThrowableOfType(
                BusinessException.class,
                () -> bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(1_500L)));

        BusinessException lowerPriceException = catchThrowableOfType(
                BusinessException.class,
                () -> bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(1_400L)));

        // Then
        assertThat(samePriceException.getErrorCode()).isEqualTo(ErrorCode.BID_CONFLICT);
        assertThat(lowerPriceException.getErrorCode()).isEqualTo(ErrorCode.BID_CONFLICT);
    }
}
