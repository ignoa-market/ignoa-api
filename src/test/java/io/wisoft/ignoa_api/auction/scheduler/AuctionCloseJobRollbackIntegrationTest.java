package io.wisoft.ignoa_api.auction.scheduler;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.entity.BidStatus;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuctionCloseJobRollbackIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @MockitoBean
    ChatRoomService chatRoomService;

    @Autowired
    AuctionCloseJob auctionCloseJob;

    @Autowired
    BidService bidService;

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
        bidRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 한_경매의_후속_처리가_실패해도_다른_경매를_계속_마감하고_다음_주기에_재시도한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));

        Item retryItem = itemRepository.save(newItem(seller));
        bidService.placeBid(retryItem.getId(), bidder.getId(), new BidCreateRequest(2_000L));
        expire(retryItem.getId(), LocalDateTime.now().minusMinutes(2));

        Item noBidItem = itemRepository.save(
                newItem(seller, LocalDateTime.now().minusMinutes(1))
        );

        doThrow(new RuntimeException("채팅방 생성 실패"))
                .doNothing()
                .when(chatRoomService)
                .createChat(retryItem.getId());

        // When: 첫 번째 실행에서 retryItem은 롤백되고, noBidItem은 계속 처리된다.
        auctionCloseJob.closeExpiredAuctions();

        // Then
        Item rolledBackItem = itemRepository.findById(retryItem.getId()).orElseThrow();
        Item closedNoBidItem = itemRepository.findById(noBidItem.getId()).orElseThrow();

        assertThat(rolledBackItem.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(bidRepository.findByItemId(retryItem.getId()))
                .singleElement()
                .extracting(Bid::getStatus)
                .isEqualTo(BidStatus.ACTIVE);
        assertThat(closedNoBidItem.getStatus()).isEqualTo(ItemStatus.NO_BID_CLOSED);

        // When: 다음 실행에서 롤백된 경매를 다시 처리한다.
        auctionCloseJob.closeExpiredAuctions();

        // Then
        Item retriedItem = itemRepository.findById(retryItem.getId()).orElseThrow();

        assertThat(retriedItem.getStatus()).isEqualTo(ItemStatus.BID_CLOSED);
        assertThat(bidRepository.findByItemId(retryItem.getId()))
                .singleElement()
                .extracting(Bid::getStatus)
                .isEqualTo(BidStatus.WON);
        verify(chatRoomService, times(2)).createChat(retryItem.getId());
    }

    private void expire(Long itemId, LocalDateTime endAt) {
        jdbcTemplate.update(
                "UPDATE items SET end_at = ? WHERE id = ?",
                endAt,
                itemId
        );
    }
}
