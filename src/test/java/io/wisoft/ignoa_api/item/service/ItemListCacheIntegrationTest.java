package io.wisoft.ignoa_api.item.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewCursorRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemViewType;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ItemListCacheIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @MockitoBean
    ChatRoomService chatRoomService;

    @MockitoSpyBean
    ItemQueryService itemQueryService;

    @Autowired
    ItemFacade itemFacade;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    WishRepository wishRepository;

    @Autowired
    MeterRegistry meterRegistry;

    // 캐시는 컨텍스트 동안 유지되므로, 테스트마다 다른 키(view, size)를 써서 서로 영향을 주지 않게 한다.
    @BeforeEach
    void setUp() {
        clearInvocations(itemQueryService);
    }

    @AfterEach
    void tearDown() {
        wishRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 같은_첫_페이지를_다시_조회하면_DB를_조회하지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        itemRepository.save(newItem(seller));
        ItemPreviewCursorRequest request = new ItemPreviewCursorRequest(ItemViewType.LATEST, null, 20);

        // When
        itemFacade.getItems(request, null);
        itemFacade.getItems(request, null);

        // Then
        verify(itemQueryService, times(1)).getItems(any(), eq(null));
    }

    @Test
    void 캐시된_목록이어도_찜_여부는_요청한_사용자_기준이다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        User wisher = userRepository.save(newUser("wisher@test.com", "찜한사람"));
        User other = userRepository.save(newUser("other@test.com", "다른사람"));
        Item item = itemRepository.save(newItem(seller));
        wishRepository.save(Wish.create(wisher, item));
        ItemPreviewCursorRequest request = new ItemPreviewCursorRequest(ItemViewType.LATEST, null, 19);

        // When
        CursorResponse<ItemPreview> wisherPage = itemFacade.getItems(request, wisher.getId());
        CursorResponse<ItemPreview> otherPage = itemFacade.getItems(request, other.getId());

        // Then
        assertThat(wisherPage.content()).extracting(ItemPreview::isWished).containsExactly(true);
        assertThat(otherPage.content()).extracting(ItemPreview::isWished).containsExactly(false);
        verify(itemQueryService, times(1)).getItems(any(), eq(null));
    }

    @Test
    void 캐시_조회_결과가_적중률_지표로_기록된다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        itemRepository.save(newItem(seller));
        ItemPreviewCursorRequest request = new ItemPreviewCursorRequest(ItemViewType.POPULAR, null, 5);
        double hitsBefore = cacheGets("hit");
        double missesBefore = cacheGets("miss");

        // When
        itemFacade.getItems(request, null);
        itemFacade.getItems(request, null);

        // Then
        assertThat(cacheGets("miss") - missesBefore).isEqualTo(1);
        assertThat(cacheGets("hit") - hitsBefore).isEqualTo(1);
    }

    @Test
    void 커서가_있는_스크롤_요청은_캐시를_거치지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Item older = itemRepository.save(newItem(seller));
        Item newer = itemRepository.save(newItem(seller));
        ItemPreviewCursorRequest request = new ItemPreviewCursorRequest(ItemViewType.LATEST, newer.getId(), 20);

        // When
        CursorResponse<ItemPreview> first = itemFacade.getItems(request, null);
        CursorResponse<ItemPreview> second = itemFacade.getItems(request, null);

        // Then
        assertThat(first.content()).extracting(ItemPreview::itemId).containsExactly(older.getId());
        assertThat(second.content()).extracting(ItemPreview::itemId).containsExactly(older.getId());
        verify(itemQueryService, times(2)).getItems(eq(request), eq(null));
    }

    private double cacheGets(String result) {
        return meterRegistry.get("cache.gets")
                .tags("cache", ItemListCache.CACHE_NAME, "result", result)
                .functionCounter()
                .count();
    }
}
