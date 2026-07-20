package io.wisoft.ignoa_api.item.service;

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
}
