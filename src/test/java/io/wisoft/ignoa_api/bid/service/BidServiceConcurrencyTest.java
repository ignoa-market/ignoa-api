package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BidServiceConcurrencyTest {

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
    void 같은_상품에_동시_입찰하면_한_건만_성공한다() throws InterruptedException {
        // Given
        long bidPrice = 1_100L;
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));
        Item item = itemRepository.save(newItem(seller));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        
        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(bidPrice));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
        assertThat(bidRepository.findByItemId(item.getId())).hasSize(1);
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

        // When
        for (int i = 0; i < threadCount; i++) {
            long bidPrice = 1_100L + i * 100L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    bidService.placeBid(item.getId(), bidder.getId(), new BidCreateRequest(bidPrice));
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        Item finalItem = itemRepository.findById(item.getId()).orElseThrow();
        long maxSavedBid = bidRepository.findByItemId(item.getId()).stream()
                .mapToLong(Bid::getPrice)
                .max()
                .orElse(0L);

        assertThat(finalItem.getCurrentPrice()).isEqualTo(2_000L);
        assertThat(finalItem.getCurrentPrice()).isEqualTo(maxSavedBid);
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
        assertThat(samePriceException.getErrorCode()).isEqualTo(ErrorCode.INVALID_BID_PRICE);
        assertThat(lowerPriceException.getErrorCode()).isEqualTo(ErrorCode.INVALID_BID_PRICE);
    }

    private static User newUser(String email, String nickname) {
        return new User(email, "password", nickname, "address");
    }

    private static Item newItem(User seller) {
        return Item.create(
                seller,
                "테스트 상품",
                "설명",
                "카테고리",
                ItemCondition.GOOD,
                "브랜드",
                1_000L,
                1_000_000L,
                LocalDateTime.now().plusDays(1)
        );
    }
}