package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.repository.ItemCommandRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ItemConditionalUpdateIntegrationTest extends IntegrationTestSupport {
    @MockitoBean AuctionCloseScheduler scheduler;
    @Autowired ItemRepository items;
    @Autowired ItemCommandRepository itemCommandRepository;
    @Autowired UserRepository users;

    enum Operation { BID, CLOSE, DELETE, EXTEND, RESERVE, COMPLETE, CANCEL }

    @ParameterizedTest(name = "{0}: 조건 충족 시 갱신, 조건 불충족 시 거절")
    @EnumSource(Operation.class)
    void 조건부_갱신은_허용된_횟수만큼만_성공한다(Operation operation) {
        User seller = users.save(newUser("conditional-seller@test.com", "판매자"));
        User buyer = users.save(newUser("conditional-buyer@test.com", "구매자"));
        Item item = items.saveAndFlush(newItem(seller));
        if (operation == Operation.COMPLETE || operation == Operation.CANCEL) {
            assertThat(itemCommandRepository.reserveBuyNowIfActive(item.getId(), item.getBuyNowPrice(), item.getEndAt().minusDays(1)))
                    .isEqualTo(1);
        }
        int allowedUpdates = operation == Operation.EXTEND ? 3 : 1;
        for (int i = 1; i <= allowedUpdates; i++) {
            assertThat(apply(operation, item, buyer)).isEqualTo(1);
        }
        assertThat(apply(operation, item, buyer)).isZero();
    }

    private int apply(Operation operation, Item item, User buyer) {
        return switch (operation) {
            case BID -> itemCommandRepository.raiseCurrentPriceIfHigher(item.getId(), 2_000L, buyer, item.getEndAt().minusDays(1));
            case CLOSE -> itemCommandRepository.closeIfExpired(item.getId(), item.getEndAt());
            case DELETE -> itemCommandRepository.softDeleteIfActive(item.getId());
            case EXTEND -> itemCommandRepository.extendIfActive(item.getId(), item.getEndAt().minusDays(1));
            case RESERVE -> itemCommandRepository.reserveBuyNowIfActive(item.getId(), item.getBuyNowPrice(), item.getEndAt().minusDays(1));
            case COMPLETE -> itemCommandRepository.completeBuyNowIfReserved(item.getId(), buyer);
            case CANCEL -> itemCommandRepository.cancelBuyNowIfReserved(item.getId());
        };
    }

}
