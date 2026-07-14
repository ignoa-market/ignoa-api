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
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

    private final ItemReader itemReader;
    private final ApplicationEventPublisher eventPublisher;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public ItemIdResponse createItem(
            Long sellerId, ItemCreateRequest request, List<UploadedMedia> uploadedMedias
    ) {
        User seller = userQueryService.findById(sellerId);

        Item item = Item.create(
                seller, request.title(), request.description(),
                request.category(), request.itemCondition(), request.brand(),
                request.startPrice(), request.buyNowPrice(), request.endAt()
        );

        List<ItemMedia> itemMedias = toItemMedias(uploadedMedias, item);

        itemRepository.save(item);
        itemMediaService.saveAll(itemMedias);
        eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));

        return new ItemIdResponse(item.getId());
    }

    public ItemDetail updateItem(
            Long itemId, Long userId, ItemUpdateRequest request, List<UploadedMedia> uploadedMedias
    ) {
        Item item = itemReader.getByIdWithSeller(itemId);

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_UPDATE_FORBIDDEN);
        }

        if (item.isClosed()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        if (!item.isValidBuyNowPrice(request.buyNowPrice())) {
            throw new BusinessException(ErrorCode.INVALID_BUY_NOW_PRICE);
        }

        item.update(
                request.title(), request.description(),
                request.category(), request.brand(), request.itemCondition(),
                request.buyNowPrice(), request.endAt()
        );

        if (!CollectionUtils.isEmpty(request.deleteMediaIds())) {
            itemMediaService.validateMediaCount(itemId, request.deleteMediaIds(), uploadedMedias);
            itemMediaService.deleteMedias(itemId, request.deleteMediaIds());
        }

        if (!CollectionUtils.isEmpty(uploadedMedias)) {
            List<ItemMedia> itemMedias = toItemMedias(uploadedMedias, item);
            itemMediaService.saveAll(itemMedias);
        }

        if (request.endAt() != null) {
            eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));
        }

        return itemQueryService.getItem(itemId, userId);
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        Item item = itemReader.getByIdWithSeller(itemId);

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
        itemMediaService.deleteAllMedia(itemId);
        itemRepository.delete(item);

        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new ItemIdResponse(item.getId());
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId) {
        Item item = itemReader.getById(itemId);
        User user = userQueryService.findById(buyerId);

        if (item.isSeller(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_BUY_NOT_ALLOWED);
        }

        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        int updatedRows = itemRepository.buyNowIfActive(
                itemId, user, ItemStatus.BUY_NOW_CLOSED, ItemStatus.ACTIVE);

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        bidService.closeBids(itemId);
        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new BuyNowResponse(itemId, buyerId, item.getBuyNowPrice(), ItemStatus.BUY_NOW_CLOSED);
    }

    private List<ItemMedia> toItemMedias(List<UploadedMedia> uploadedMedias, Item item) {
        return uploadedMedias.stream()
                .map(uploadedMedia -> ItemMedia.from(
                        item,
                        uploadedMedia.mediaUrl(),
                        uploadedMedia.mediaType()
                )).toList();
    }
}
