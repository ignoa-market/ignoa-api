package io.wisoft.ignoa_api.bid.facade;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.request.BidListRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.dto.response.BidSummary;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.util.RedisLockUtils;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidFacade {

    private final ApplicationEventPublisher eventPublisher;
    private final BidRepository bidRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final RedisLockUtils redisLockUtils;

    public BidResponse placeBid(Long itemId, Long bidderId, BidCreateRequest request) {
        final User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return redisLockUtils.executeWithLock(
                getPlaceBidKey(itemId),
                "",
                Duration.ofMinutes(1),
                () -> {
                    Item item = itemRepository.findById(itemId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

                    if (item.isSeller(bidderId)) {
                        throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
                    }

                    if (!item.isActive()) {
                        throw new BusinessException(ErrorCode.AUCTION_CLOSED);
                    }

                    if (!item.isValidBidPrice(request.price())) {
                        throw new BusinessException(ErrorCode.INVALID_BID_PRICE);
                    }

                    item.raisePriceTo(request.price());

                    final Bid bid = Bid.place(item, bidder, request.price());
                    bidRepository.save(bid);
                    eventPublisher.publishEvent(BidPlaceEvent.of(bid, item, bidder));

                    return BidResponse.from(bid);
                });
    }

    private static String getPlaceBidKey(Long itemId) {
        return "placeBid:item:" + itemId;
    }
}
