package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemListRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.*;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.event.ItemDeletedEvent;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemMediaService itemMediaService;
    private final ApplicationEventPublisher eventPublisher;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;

    @Transactional
    public ItemResponse createItem(Long sellerId, ItemCreateRequest request, List<MultipartFile> files) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
        itemMediaService.saveMedia(item, files);
        eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));

        return new ItemResponse(item.getId());
    }

    public SliceResponse<ItemPreview> getItems(Long userId, ItemListRequest request) {
        Slice<Item> itemSlice = getItemsByView(request, userId, PageRequest.of(request.page(), request.size()));

        List<ItemPreview> itemPreviews = itemSlice.getContent().stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        getWishCount(item.getId())
                )).toList();

        return SliceResponse.of(itemPreviews, itemSlice.hasNext());
    }

    private Slice<Item> getItemsByView(ItemListRequest request, Long userId, Pageable pageable) {
        if (request.view().requiresAuth() && userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return switch (request.view()) {
            case ALL, LATEST -> itemRepository.findLatestItems(request.category(), pageable);
            case POPULAR -> itemRepository.findPopularItems(request.category(), pageable);
            case ENDING_SOON -> itemRepository.findEndingSoonItems(request.category(), pageable);
            case MY_ITEMS -> itemRepository.findMyItems(request.category(), userId, pageable);
            case MY_BIDS -> itemRepository.findMyBidItems(request.category(), userId, pageable);
        };
    }

    private int getWishCount(Long itemId) {
        return wishRepository.countByItemId(itemId);
    }

    private int getBidCount(Long itemId) {
        return bidRepository.countDistinctBidderByItemId(itemId);
    }

    @Transactional
    public ItemDetail getItemDetail(Long itemId, Long userId) {
        Item item = itemRepository.findByIdWithSeller(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        item.increaseViewCount();

        SellerProfile sellerInfo = SellerProfile.from(item.getSeller());

        Optional<Bid> topBid = userId != null
                ? bidRepository.findTopByBidderIdAndItemIdOrderByPriceDesc(userId, itemId)
                : Optional.empty();
        boolean isBidder = topBid.isPresent();
        boolean isTopBidder = topBid.map(bid -> bid.getPrice().equals(item.getCurrentPrice())).orElse(false);
        boolean isSeller = userId != null && item.isSeller(userId);
        List<ItemMediaInfo> mediaUrls = itemMediaService.getMediaInfoByItemId(itemId);
        int wishCount = getWishCount(itemId);
        int bidCount = getBidCount(itemId);
        boolean isWished = userId != null && wishRepository.existsByUserIdAndItemId(userId, itemId);

        return ItemDetail.of(item, sellerInfo, isTopBidder, isBidder, isSeller, mediaUrls, wishCount, bidCount, isWished);
    }

    @Transactional
    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files) {
        Item item = itemRepository.findByIdWithSeller(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_UPDATE_FORBIDDEN);
        }

        if (item.isClosed()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        if (request.buyNowPrice() != null && request.buyNowPrice() < item.getCurrentPrice()) {
            throw new BusinessException(ErrorCode.INVALID_BUY_NOW_PRICE);
        }

        item.updateInfo(request.title(), request.description(), request.category(), request.brand(),
                request.itemCondition(), request.buyNowPrice(), request.endAt());

        if (request.deleteMediaIds() != null && !request.deleteMediaIds().isEmpty()) {
            itemMediaService.validateMinimumMediaCount(itemId, request.deleteMediaIds(), files);
            itemMediaService.deleteMediaByIds(itemId, request.deleteMediaIds());
        }

        if (files != null && !files.isEmpty()) {
            itemMediaService.saveMedia(item, files);
        }

        eventPublisher.publishEvent(new AuctionRegisteredEvent(item.getId(), item.getEndAt()));

        return getItemDetail(itemId, userId);
    }

    @Transactional
    public ItemResponse deleteItem(Long itemId, Long userId) {
        Item item = itemRepository.findByIdWithSeller(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        if (!item.isSeller(userId)) {
            throw new BusinessException(ErrorCode.ITEM_DELETE_FORBIDDEN);
        }

        itemMediaService.deleteAllByItemId(itemId);
        bidRepository.deleteAllByItemId(itemId);
        wishRepository.deleteAllByItemId(itemId);
        itemRepository.delete(item);
        eventPublisher.publishEvent(new ItemDeletedEvent(itemId));

        return new ItemResponse(item.getId());
    }
}
