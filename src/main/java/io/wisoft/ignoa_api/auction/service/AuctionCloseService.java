package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseService {

    private final BidService bidService;
    private final ItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long itemId) {
        int closedRows = itemRepository.closeIfActive(itemId);

        if (closedRows == 0) {
            log.info("[중복 마감 방지] 이미 마감된 경매 itemId={}", itemId);
            return;
        }

        boolean sold = bidService.markBidResults(itemId);
        log.info(sold ? "[낙찰] 경매 마감 itemId={}" : "[유찰] 입찰 없이 마감된 경매 itemId={}", itemId);
    }
}
