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
            log.info("[유찰] 입찰 없이 마감된 경매 itemId={}", itemId);
            item.closeWithoutBid();
            return;
        }

        // 입찰 개수만큼 개별 UPDATE O(n)이 발생한다.
        // 대량 입찰/동시 마감으로 write burst가 커지면 벌크 UPDATE로 전환 검토가 필요하다.
        // (현재 트래픽에선 불필요)
        bidService.closeBids(itemId);
        Bid topBid = bid.get();
        topBid.closeAsWon();

        User winner = topBid.getBidder();
        item.closeWithWinner(winner);
    }
}
