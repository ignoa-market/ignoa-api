package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.entity.enums.TradeType;
import io.wisoft.ignoa_api.trade.repository.TradeRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserWithdrawTradeTest extends IntegrationTestSupport {

    @Autowired
    private UserCommandService userCommandService;

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
        jdbcTemplate.update("DELETE FROM items");
        userRepository.deleteAllInBatch();
    }

    @Test
    void 결제_대기_거래의_구매자와_판매자는_탈퇴할_수_없다() {
        // Given: 경매가 끝나 상품은 닫혔지만 거래는 결제 대기 중
        User seller = userRepository.save(newUser("seller@test.com", "seller"));
        User buyer = userRepository.save(newUser("buyer@test.com", "buyer"));
        Item item = itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(1)));
        tradeRepository.save(Trade.create(
                item, buyer, TradeType.AUCTION, 1_000L, LocalDateTime.now().plusDays(1)));
        jdbcTemplate.update("UPDATE items SET status = 'BID_CLOSED' WHERE id = ?", item.getId());

        // When & Then
        assertThatThrownBy(() -> userCommandService.withdraw(buyer.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HAS_UNFINISHED_TRADE);
        assertThatThrownBy(() -> userCommandService.withdraw(seller.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.HAS_UNFINISHED_TRADE);
    }
}
