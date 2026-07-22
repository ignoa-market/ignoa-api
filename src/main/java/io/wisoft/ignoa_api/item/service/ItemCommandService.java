package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.event.AuctionClosedEvent;
import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.metrics.MeasureTransaction;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemCommandService {

    private final ItemMediaService itemMediaService;
    private final ItemQueryService itemQueryService;
    private final UserQueryService userQueryService;

    private final ItemReader itemReader;
    private final ApplicationEventPublisher eventPublisher;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public ItemIdResponse createItem(Long sellerId, ItemCreateRequest request, List<UploadedMedia> uploadedMedias) {
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

    @MeasureTransaction(operation = LockOperation.UPDATE)
    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<UploadedMedia> uploadedMedias) {
        Item item = itemReader.getByIdWithSeller(itemId);

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_UPDATE_FORBIDDEN);
        }

        if (!item.isActive()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        validateBuyNowPrice(item, itemId, request.buyNowPrice());

        item.update(request.title(), request.description(), request.category(),
                request.brand(), request.itemCondition(), request.buyNowPrice()
        );

        if (!CollectionUtils.isEmpty(request.deleteMediaIds())) {
            itemMediaService.validateMediaCount(itemId, request.deleteMediaIds(), uploadedMedias);
            itemMediaService.deleteMedias(itemId, request.deleteMediaIds());
        }

        if (!CollectionUtils.isEmpty(uploadedMedias)) {
            itemMediaService.saveAll(toItemMedias(uploadedMedias, item));
        }

        return itemQueryService.getItem(itemId, userId);
    }

    @MeasureTransaction(operation = LockOperation.DELETE)
    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        Item item = itemReader.getByIdWithSeller(itemId);

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_DELETE_FORBIDDEN);
        }

        int updatedRows = itemRepository.softDeleteIfActive(itemId);

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.ITEM_DELETE_CONFLICT);
        }

        wishRepository.deleteAllByItemId(itemId);
        itemMediaService.deleteAllMedia(itemId);
        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new ItemIdResponse(itemId);
    }

    @MeasureTransaction(operation = LockOperation.BUY_NOW)
    public BuyNowResponse buyNowItem(Long itemId, Long buyerId, ItemBuyNowRequest request) {
        Item item = itemReader.getById(itemId);
        User user = userQueryService.findById(buyerId);

        if (item.isSeller(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_BUY_NOT_ALLOWED);
        }

        int updatedRows = itemRepository.buyNowIfActive(itemId, user, request.buyNowPrice(), LocalDateTime.now());

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }

        bidRepository.markLosingBids(itemId);
        eventPublisher.publishEvent(new AuctionClosedEvent(itemId));

        return new BuyNowResponse(itemId, buyerId, request.buyNowPrice(), ItemStatus.BUY_NOW_CLOSED);
    }

    private void validateBuyNowPrice(Item item, Long itemId, Long buyNowPrice) {
        // 즉시구매가 변경 시, 입찰 이력이 있으면 변경 불가
        if (item.isBuyNowPriceChanged(buyNowPrice)
                && bidRepository.existsByItemId(itemId)) {
            throw new BusinessException(ErrorCode.BUY_NOW_PRICE_CHANGED_NOT_ALLOWED);
        }

        // 즉시구매가는 현재 입찰가보다 높아야 함
        if (!item.isValidBuyNowPrice(buyNowPrice)) {
            throw new BusinessException(ErrorCode.INVALID_BUY_NOW_PRICE);
        }
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
