package io.wisoft.ignoa_api.auction.scheduler;

import io.wisoft.ignoa_api.auction.service.AuctionCloseService;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionCloseJob {

    private final AuctionCloseService auctionCloseService;
    private final ItemRepository itemRepository;

    public void closeExpiredAuctions() {
        List<Item> expiredItems = itemRepository
                .findAllByStatusAndEndAtBefore(ItemStatus.ACTIVE, LocalDateTime.now());

        for (Item item : expiredItems) {
            try {
                auctionCloseService.closeAuction(item.getId());
            } catch (Exception e) {
                log.error("경매 마감 처리 실패 itemId={}", item.getId(), e);
            }
        }
    }
}
