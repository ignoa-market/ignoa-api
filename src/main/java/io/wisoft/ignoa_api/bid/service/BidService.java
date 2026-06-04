package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.auction.event.AuctionClosedEvent;
import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.request.BidListRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidSummary;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final ApplicationEventPublisher eventPublisher;

    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Item item = itemRepository.findByIdWithLock(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        Long bidPrice = request.price();

        if (item.isSeller(bidderId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }

        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.AUCTION_CLOSED);
        }

        if (!item.isValidBidPrice(bidPrice)) {
            throw new BusinessException(ErrorCode.INVALID_BID_PRICE);
        }

        if (item.isReachedBuyNowPrice(bidPrice)) {
            throw new BusinessException(ErrorCode.BID_PRICE_EXCEEDS_BUY_NOW);
        }

        item.raiseBidPrice(bidPrice);
        Bid bid = Bid.place(item, bidder, bidPrice);
        bidRepository.save(bid);

        eventPublisher.publishEvent(BidPlaceEvent.of(bid, item, bidder));

        return BidResponse.from(bid);
    }

    @Transactional
    public void closeBids(Long itemId) {
        bidRepository.findByItemId(itemId)
                .forEach(Bid::closeAsLost);
    }

    public SliceResponse<BidSummary> getBids(Long itemId, BidListRequest request) {
        if (!itemRepository.existsById(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }

        Slice<Bid> bidSlice = bidRepository.findByItemIdWithBidder(
                itemId, PageRequest.of(request.page(), request.size()));

        List<BidSummary> bidSummaries = bidSlice.getContent().stream()
                .map(BidSummary::from)
                .toList();

        return SliceResponse.of(bidSummaries, bidSlice.hasNext());
    }
}
