package io.wisoft.ignoa_api.auction.scheduler;

import io.wisoft.ignoa_api.auction.service.AuctionService;
import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.entity.BidStatus;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionCloseJobIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @Autowired
    AuctionCloseJob auctionCloseJob;

    @Autowired
    AuctionService auctionService;

    @Autowired
    BidService bidService;

    @Autowired
    ChatRoomRepository chatRoomRepository;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        chatRoomRepository.deleteAllInBatch();
        bidRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 입찰이_없는_만료_경매만_유찰_처리한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Item expiredItem = itemRepository.save(
                newItem(seller, LocalDateTime.now().minusMinutes(1))
        );
        Item activeItem = itemRepository.save(
                newItem(seller, LocalDateTime.now().plusDays(1))
        );

        // When
        auctionCloseJob.closeExpiredAuctions();

        // Then
        Item closedItem = itemRepository.findById(expiredItem.getId()).orElseThrow();
        Item unchangedItem = itemRepository.findById(activeItem.getId()).orElseThrow();

        assertThat(closedItem.getStatus()).isEqualTo(ItemStatus.NO_BID_CLOSED);
        assertThat(unchangedItem.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    void 입찰이_있는_만료_경매는_낙찰과_패찰을_결정하고_채팅방을_한번만_생성한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User firstBidder = userRepository.save(newUser("bidder1@test.com", "입찰자1"));
        User winningBidder = userRepository.save(newUser("bidder2@test.com", "입찰자2"));
        Item item = itemRepository.save(newItem(seller));

        bidService.placeBid(item.getId(), firstBidder.getId(), new BidCreateRequest(1_500L));
        bidService.placeBid(item.getId(), winningBidder.getId(), new BidCreateRequest(2_000L));
        expire(item.getId(), LocalDateTime.now().minusMinutes(1));

        // When
        auctionCloseJob.closeExpiredAuctions();
        auctionCloseJob.closeExpiredAuctions();

        // Then
        Item closedItem = itemRepository.findById(item.getId()).orElseThrow();
        Map<Long, BidStatus> statusByPrice = bidRepository.findByItemId(item.getId()).stream()
                .collect(Collectors.toMap(Bid::getPrice, Bid::getStatus));
        List<ChatRoom> chatRooms = chatRoomRepository.findAll();

        assertThat(closedItem.getStatus()).isEqualTo(ItemStatus.BID_CLOSED);
        assertThat(closedItem.getHighestBidder().getId()).isEqualTo(winningBidder.getId());
        assertThat(statusByPrice)
                .hasSize(2)
                .containsEntry(1_500L, BidStatus.LOST)
                .containsEntry(2_000L, BidStatus.WON);
        assertThat(chatRooms).singleElement().satisfies(chatRoom -> {
            assertThat(chatRoom.getItem().getId()).isEqualTo(item.getId());
            assertThat(chatRoom.getSeller().getId()).isEqualTo(seller.getId());
            assertThat(chatRoom.getBuyer().getId()).isEqualTo(winningBidder.getId());
        });
    }

    @Test
    void 분산_락_없이_동시에_마감해도_조건부_UPDATE로_한번만_처리한다() throws InterruptedException {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User firstBidder = userRepository.save(newUser("bidder1@test.com", "입찰자1"));
        User winningBidder = userRepository.save(newUser("bidder2@test.com", "입찰자2"));
        Item item = itemRepository.save(newItem(seller));

        bidService.placeBid(item.getId(), firstBidder.getId(), new BidCreateRequest(1_500L));
        bidService.placeBid(item.getId(), winningBidder.getId(), new BidCreateRequest(2_000L));
        expire(item.getId(), LocalDateTime.now().minusMinutes(1));

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        auctionService.closeAuction(item.getId());
                    } catch (Throwable throwable) {
                        unexpectedErrors.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        }

        // Then
        Item closedItem = itemRepository.findById(item.getId()).orElseThrow();
        Map<Long, BidStatus> statusByPrice = bidRepository.findByItemId(item.getId()).stream()
                .collect(Collectors.toMap(Bid::getPrice, Bid::getStatus));

        assertThat(unexpectedErrors).isEmpty();
        assertThat(closedItem.getStatus()).isEqualTo(ItemStatus.BID_CLOSED);
        assertThat(statusByPrice)
                .hasSize(2)
                .containsEntry(1_500L, BidStatus.LOST)
                .containsEntry(2_000L, BidStatus.WON);
        assertThat(chatRoomRepository.count()).isEqualTo(1L);
    }

    private void expire(Long itemId, LocalDateTime endAt) {
        jdbcTemplate.update(
                "UPDATE items SET end_at = ? WHERE id = ?",
                endAt,
                itemId
        );
    }
}
