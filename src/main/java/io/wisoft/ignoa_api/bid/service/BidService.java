package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidHistory;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final UserQueryService userQueryService;
    private final ItemReader itemReader;
    private final ApplicationEventPublisher eventPublisher;

    private final BidRepository bidRepository;
    private final ItemRepository itemRepository;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        Long bidPrice = request.price();
        User bidder = userQueryService.findById(bidderId);
        Item item = itemReader.getById(itemId);

        if (item.isSeller(bidderId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }

        int updatedRows = itemRepository.raiseCurrentPriceIfHigher(itemId, bidPrice, bidder, LocalDateTime.now());

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BID_CONFLICT);
        }

        Bid bid = Bid.place(item, bidder, bidPrice);
        bidRepository.save(bid);
        eventPublisher.publishEvent(BidPlaceEvent.of(bid, item, bidder));

        return BidResponse.from(bid);
    }

    @Transactional
    public boolean markBidResults(Long itemId) {
        if (bidRepository.markWinningBid(itemId) == 0) {
            return false;
        }

        bidRepository.markLosingBids(itemId);
        return true;
    }

    public List<BidHistory> getBids(Long itemId) {
        if (!itemRepository.existsById(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }

        return bidRepository.findByItemIdWithBidder(itemId).stream()
                .map(BidHistory::from)
                .toList();
    }
}
