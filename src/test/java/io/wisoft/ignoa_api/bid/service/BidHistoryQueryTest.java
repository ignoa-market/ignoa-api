package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidHistoryCursorRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidHistory;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BidHistoryQueryTest extends IntegrationTestSupport {

    @Autowired
    BidService bidService;

    @Autowired
    BidRepository bidRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ItemRepository itemRepository;

    User bidder;
    Item item;

    @BeforeEach
    void setUp() {
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        bidder = userRepository.save(newUser("bidder@test.com", "입찰자"));
        item = itemRepository.save(newItem(seller));
    }

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    // 입찰은 현재가보다 높을 때만 저장되므로 가격 오름차순으로 저장해 실제 순서를 재현한다
    private List<Long> saveBids(Item target, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add(bidRepository.save(Bid.place(target, bidder, 1_000L + i * 100L)).getId());
        }
        return ids;
    }

    private List<Long> idsOf(CursorResponse<BidHistory> response) {
        return response.content().stream().map(BidHistory::bidId).toList();
    }

    @Test
    void 첫_페이지는_최신_입찰부터_size건과_다음_커서를_반환한다() {
        // Given
        List<Long> ids = saveBids(item, 25);

        // When
        CursorResponse<BidHistory> response =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(null, 20));

        // Then
        List<Long> expected = ids.reversed().subList(0, 20);
        assertThat(idsOf(response)).containsExactlyElementsOf(expected);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(expected.getLast());
        assertThat(response.content().getFirst().bidderNickname()).isEqualTo("입찰자");
    }

    @Test
    void 다음_커서로_조회하면_이전_입찰만_중복_없이_반환하고_끝이면_커서가_없다() {
        // Given
        List<Long> ids = saveBids(item, 25);
        CursorResponse<BidHistory> first =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(null, 20));

        // When
        CursorResponse<BidHistory> second =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(first.nextCursor(), 20));

        // Then
        assertThat(idsOf(second)).containsExactlyElementsOf(ids.reversed().subList(20, 25));
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 남은_입찰이_정확히_size건이면_다음_페이지가_없다() {
        // Given
        saveBids(item, 20);

        // When
        CursorResponse<BidHistory> response =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(null, 20));

        // Then
        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 다른_상품의_입찰은_섞이지_않는다() {
        // Given
        Item otherItem = itemRepository.save(newItem(userRepository.save(newUser("other@test.com", "다른판매자"))));
        List<Long> ids = saveBids(item, 3);
        saveBids(otherItem, 3);

        // When
        CursorResponse<BidHistory> response =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(null, 20));

        // Then
        assertThat(idsOf(response)).containsExactlyElementsOf(ids.reversed());
    }

    @Test
    void 입찰이_없으면_빈_목록을_반환한다() {
        // When
        CursorResponse<BidHistory> response =
                bidService.getBids(item.getId(), new BidHistoryCursorRequest(null, 20));

        // Then
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 없는_상품이면_ITEM_NOT_FOUND_예외가_발생한다() {
        // When
        BusinessException exception = catchThrowableOfType(BusinessException.class,
                () -> bidService.getBids(Long.MAX_VALUE, new BidHistoryCursorRequest(null, 20)));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }
}
