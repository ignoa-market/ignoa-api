package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.event.AuctionClosedEvent;
import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemIdResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemCommandService {

    private final ItemMediaService itemMediaService;
    private final ItemQueryService itemQueryService;
    private final UserQueryService userQueryService;
    private final BidService bidService;

    private final ApplicationEventPublisher eventPublisher;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public ItemIdResponse createItem(
            Long sellerId, ItemCreateRequest request, List<MultipartFile> files
    ) {
        if (request.startPrice() >= request.buyNowPrice()) {
            throw new BusinessException(ErrorCode.INVALID_BUY_NOW_PRICE_ON_CREATE);
        }

        User seller = userQueryService.findById(sellerId);

        Item item = Item.create(
                seller,
                request.title(),
                request.description(),
                request.category(),
                request.itemCondition(),
                request.startPrice(),
                request.buyNowPrice(),
                request.brand(),
                request.endAt()
        );


        itemRepository.save(item);
        itemMediaService.save(item, files);
        eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));

        return new ItemIdResponse(item.getId());
    }

    public ItemDetail updateItem(
            Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files
    ) {
        Item item = itemQueryService.getItemWithSeller(itemId);

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_UPDATE_FORBIDDEN);
        }

        if (item.isClosed()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        if (!item.isValidBuyNowPrice(request.buyNowPrice())) {
            throw new BusinessException(ErrorCode.INVALID_BUY_NOW_PRICE);
        }

        item.update(request.title(),
                request.description(),
                request.category(),
                request.brand(),
                request.itemCondition(),
                request.buyNowPrice(),
                request.endAt());

        if (!CollectionUtils.isEmpty(request.deleteMediaIds())) {
            itemMediaService.validateMinimumMediaCount(itemId, request.deleteMediaIds(), files);
            itemMediaService.deleteByIds(itemId, request.deleteMediaIds());
        }

        if (!CollectionUtils.isEmpty(files)) {
            itemMediaService.save(item, files);
        }

        if (request.endAt() != null) {
            eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));
        }

        return itemQueryService.getItem(itemId, userId);
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        Item item = itemQueryService.getItemWithSeller(itemId);

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_DELETE_FORBIDDEN);
        }

        if (item.isSold()) {
            throw new BusinessException(ErrorCode.SOLD_ITEM_CANNOT_BE_DELETED);
        }

        boolean hasBid = bidRepository.existsByItemId(itemId);
        if (hasBid) {
            throw new BusinessException(ErrorCode.ITEM_WITH_BID_CANNOT_BE_DELETED);
        }

        wishRepository.deleteAllByItemId(itemId);
        itemMediaService.deleteAllByItemId(itemId);
        itemRepository.delete(item);

        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new ItemIdResponse(item.getId());
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId) {
        Item item = itemRepository.findByIdWithLock(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        User user = userQueryService.findById(buyerId);

        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        if (item.isSeller(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_BUY_NOT_ALLOWED);
        }

        item.buyNow(user);
        bidService.closeBids(itemId);
        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new BuyNowResponse(itemId, buyerId, item.getBuyNowPrice(), item.getStatus());
    }
}
