package io.wisoft.ignoa_api.auction.metric;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.item.entity.Item;
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

class AuctionBacklogMetricsIntegrationTest extends IntegrationTestSupport {

    // 스케줄러가 테스트 중 만료 경매를 마감하지 않도록 비활성화
    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        itemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 밀린_경매_수는_수집_시점에_종료_시각이_지난_ACTIVE_경매만_센다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(2)));
        itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(1)));
        itemRepository.save(newItem(seller, LocalDateTime.now().plusDays(1)));
        Item closedItem = itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(3)));
        jdbcTemplate.update("UPDATE items SET status = 'AUCTION_UNSOLD' WHERE id = ?", closedItem.getId());

        // When
        Gauge backlog = meterRegistry.get("auction.close.backlog").gauge();

        // Then
        assertThat(backlog.value()).isEqualTo(2.0);
    }

    @Test
    void 밀린_경매_수는_등록_시점이_아니라_값을_읽을_때마다_다시_센다() {
        // Given
        User seller = userRepository.save(newUser("seller@test.com", "판매자"));
        Gauge backlog = meterRegistry.get("auction.close.backlog").gauge();
        assertThat(backlog.value()).isZero();

        // When
        itemRepository.save(newItem(seller, LocalDateTime.now().minusMinutes(1)));

        // Then
        assertThat(backlog.value()).isEqualTo(1.0);
    }
}
