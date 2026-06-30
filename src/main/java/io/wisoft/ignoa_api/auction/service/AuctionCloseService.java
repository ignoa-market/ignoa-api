package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
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

    private final BidService bidService;
    private final BidRepository bidRepository;
    private final ItemReader itemReader;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long itemId) {
        Item item = itemReader.getById(itemId);

        if (item.isClosed()) {
            log.info("[중복 마감 방지] 이미 마감된 경매 itemId={} status={}", itemId, item.getStatus());
            return;
        }

        Optional<Bid> bid = bidRepository.findTopByItemIdOrderByPriceDesc(itemId);

        if (bid.isEmpty()) {
            item.closeWithoutBid();
            return;
        }

        bidService.closeBids(itemId);
        Bid topBid = bid.get();
        topBid.closeAsWon();

        User winner = topBid.getBidder();
        item.closeWithWinner(winner);
    }
}
