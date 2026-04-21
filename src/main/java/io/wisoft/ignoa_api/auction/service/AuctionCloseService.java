package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseService {

    private final BidRepository bidRepository;
    private final ItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long itemId) {
        Item lockedItem = itemRepository.findByIdWithLock(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        if (lockedItem.getStatus() != ItemStatus.ACTIVE) {
            log.info("[중복 마감 방지] 이미 마감된 경매 itemId={} status={}", itemId, lockedItem.getStatus());
            return;
        }

        Optional<Bid> highestBid = bidRepository.findTopByItemIdOrderByPriceDesc(itemId);

        if (highestBid.isEmpty()) {
            lockedItem.closeAsNoBid();
            return;
        }

        Bid winningBid = highestBid.get();
        lockedItem.closeWithWinner(winningBid.getBidder());
        winningBid.closeAsWon();
    }
}
