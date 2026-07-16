package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ItemDynamicUpdateTest extends IntegrationTestSupport {

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    void buyNow_flush는_입찰이_갱신한_currentPrice를_덮어쓰지_않는다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item saved = itemRepository.save(newItem(seller));

        entityManager.flush();
        entityManager.clear();

        long itemId = saved.getId();
        long before = saved.getCurrentPrice();
        long raised = before + 1_000L;
        
        // When
        Item item = entityManager.find(Item.class, itemId);

        itemRepository.raiseCurrentPriceIfHigher(itemId, raised);

        item.buyNow(buyer);
        entityManager.flush();
        entityManager.clear();

        // Then
        Item reloaded = itemRepository.findById(itemId).orElseThrow();
        assertThat(reloaded.getCurrentPrice()).isEqualTo(raised);
    }
}
