package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ItemBuyNowTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemCommandService itemCommandService;

    @AfterEach
    void tearDown() {
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 정상적인_즉시구매는_상품을_BUY_NOW_CLOSED_상태로_마감한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller));
        ItemBuyNowRequest request = new ItemBuyNowRequest(item.getBuyNowPrice());

        // When
        BuyNowResponse response = itemCommandService.buyNowItem(item.getId(), buyer.getId(), request);

        // Then
        assertThat(response.status()).isEqualTo(ItemStatus.BUY_NOW_CLOSED);

        Item reloaded = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ItemStatus.BUY_NOW_CLOSED);
    }

    @Test
    void 동시에_즉시구매하면_한_건만_성공하고_나머지는_실패한다() throws InterruptedException {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        Item item = itemRepository.save(newItem(seller));
        ItemBuyNowRequest request = new ItemBuyNowRequest(item.getBuyNowPrice());

        int threadCount = 10;

        List<Long> buyerIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            buyerIds.add(userRepository.save(
                    newUser("buyer" + i + "@test.com", "buyer" + i)).getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // When
        for (Long buyerId : buyerIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    itemCommandService.buyNowItem(item.getId(), buyerId, request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.AUCTION_ALREADY_CLOSED) {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Then
        assertThat(successCount.get()).isOne();
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }
}
