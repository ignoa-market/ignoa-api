package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.PurchasePreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeStatus;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MyPurchasesTest extends IntegrationTestSupport {

    @Autowired
    private UserItemQueryService userItemQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM item_media");
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 낙찰과_즉시구매_거래를_최신순으로_보여주고_취소된_거래는_뺀다() {
        // Given: 낙찰(결제 대기), 즉시구매(결제 완료), 취소된 거래 각 1건
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item auctionItem = itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(1)));
        Item buyNowItem = itemRepository.save(newItem(seller));
        Item canceledItem = itemRepository.save(newItem(seller));

        Trade auction = tradeRepository.save(Trade.create(
                auctionItem, buyer, TradeType.AUCTION, 1_000L, LocalDateTime.now().plusDays(1)));
        Trade buyNow = tradeRepository.save(Trade.create(
                buyNowItem, buyer, TradeType.BUY_NOW, 2_000L, LocalDateTime.now().plusMinutes(30)));
        Trade canceled = tradeRepository.save(Trade.create(
                canceledItem, buyer, TradeType.BUY_NOW, 3_000L, LocalDateTime.now().plusMinutes(30)));
        jdbcTemplate.update("UPDATE trades SET status = 'PAID' WHERE id = ?", buyNow.getId());
        jdbcTemplate.update("UPDATE trades SET status = 'CANCELED' WHERE id = ?", canceled.getId());

        // When
        List<PurchasePreview> purchases = userItemQueryService.getMyPurchases(buyer.getId());

        // Then
        assertThat(purchases)
                .extracting(PurchasePreview::tradeId, PurchasePreview::tradeStatus, p -> p.item().itemId())
                .containsExactly(
                        tuple(buyNow.getId(), TradeStatus.PAID, buyNowItem.getId()),
                        tuple(auction.getId(), TradeStatus.PAYMENT_PENDING, auctionItem.getId()));
    }

    @Test
    void 판매자의_구매_목록에는_내가_판_거래가_나오지_않는다() {
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller));
        tradeRepository.save(Trade.create(item, buyer, TradeType.BUY_NOW, 1_000L, LocalDateTime.now().plusMinutes(30)));

        assertThat(userItemQueryService.getMyPurchases(seller.getId())).isEmpty();
    }
}
