package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.request.BidHistoryCursorRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidHistory;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.repository.ItemCommandRepository;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
    private final ItemCommandRepository itemCommandRepository;

    public CursorResponse<BidHistory> getBids(Long itemId, BidHistoryCursorRequest request) {
        if (!itemRepository.existsById(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }

        Slice<Bid> bids = bidRepository.findBidHistory(
                itemId, request.cursor(), PageRequest.of(0, request.size())
        );

        List<Bid> content = bids.getContent();
        Long nextCursor = bids.hasNext()
                ? content.get(content.size() - 1).getId()
                : null;

        List<BidHistory> response = content.stream()
                .map(BidHistory::from)
                .toList();

        return CursorResponse.of(response, nextCursor, bids.hasNext());
    }

    @Transactional
    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        Long bidPrice = request.price();
        User bidder = userQueryService.findById(bidderId);
        Item item = itemReader.getById(itemId);

        if (item.isSeller(bidderId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }

        int updatedRows = itemCommandRepository.raiseCurrentPriceIfHigher(itemId, bidPrice, bidder, LocalDateTime.now());

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BID_CONFLICT);
        }

        itemCommandRepository.increaseBidderCountIfFirstBid(itemId, bidderId);

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
}
