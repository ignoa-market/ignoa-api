package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMediaServiceIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @MockitoBean
    ChatRoomService chatRoomService;

    @Autowired
    ItemMediaService itemMediaService;

    @Autowired
    ItemMediaRepository itemMediaRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserRepository userRepository;

    @AfterEach
    void tearDown() {
        itemMediaRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 첫_미디어가_동영상이어도_가장_먼저_등록된_이미지를_대표로_반환한다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Item item = itemRepository.save(newItem(seller));

        itemMediaRepository.save(ItemMedia.from(item, "items/video.mp4", ItemMediaType.VIDEO));
        itemMediaRepository.save(ItemMedia.from(item, "items/first.jpg", ItemMediaType.IMAGE));
        itemMediaRepository.save(ItemMedia.from(item, "items/second.jpg", ItemMediaType.IMAGE));

        // When
        Map<Long, String> urls = itemMediaService.getFirstMediaUrl(List.of(item.getId()));

        // Then
        assertThat(urls.get(item.getId())).endsWith("items/first.jpg");
    }
}
