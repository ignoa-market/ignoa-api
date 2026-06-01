package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewRequest;
import io.wisoft.ignoa_api.item.dto.response.*;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemQueryService {

    private final ItemMediaService itemMediaService;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public SliceResponse<ItemPreview> getItems(ItemPreviewRequest request) {
        Slice<Item> itemSlice = getItemsByView(request, PageRequest.of(request.page(), request.size()));

        List<ItemPreview> itemPreviewList = itemSlice.getContent().stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        getWishCount(item.getId())
                )).toList();

        return SliceResponse.of(itemPreviewList, itemSlice.hasNext());
    }

    public ItemDetail getItem(Long itemId, Long userId) {
        Item item = getItemWithSeller(itemId);

        Optional<Bid> topBid = userId != null
                ? bidRepository.findTopByBidderIdAndItemIdOrderByPriceDesc(userId, itemId)
                : Optional.empty();
        boolean isBidder = topBid.isPresent();
        boolean isTopBidder = topBid.map(bid -> bid.getPrice().equals(item.getCurrentPrice())).orElse(false);
        boolean isSeller = userId != null && item.isSeller(userId);
        List<ItemMediaUrl> mediaUrls = itemMediaService.getMediaInfoByItemId(itemId);
        int wishCount = getWishCount(itemId);
        int bidCount = getBidCount(itemId);
        boolean isWished = userId != null && wishRepository.existsByUserIdAndItemId(userId, itemId);
        SellerProfile sellerInfo = SellerProfile.from(item.getSeller());

        return ItemDetail.of(item, sellerInfo, isTopBidder, isBidder, isSeller, mediaUrls, wishCount, bidCount, isWished);
    }

    private Slice<Item> getItemsByView(ItemPreviewRequest request, Pageable pageable) {
        return switch (request.view()) {
            case ALL, LATEST -> itemRepository.findLatestItems(request.category(), pageable);
            case POPULAR -> itemRepository.findPopularItems(request.category(), pageable);
            case ENDING_SOON -> itemRepository.findEndingSoonItems(request.category(), pageable);
        };
    }

    public List<ItemPreview> getMyItems(Long userId) {
        return itemRepository.findItemsBySellerId(userId).stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        getWishCount(item.getId())
                )).toList();
    }

    public List<ItemPreview> getMyBids(Long userId) {
        return itemRepository.findItemsByBidderId(userId).stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        getWishCount(item.getId())
                )).toList();
    }

    public Item getItemWithSeller(Long itemId) {
        return itemRepository.findByIdWithSeller(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    private int getWishCount(Long itemId) {
        return wishRepository.countByItemId(itemId);
    }

    private int getBidCount(Long itemId) {
        return bidRepository.countDistinctBidderByItemId(itemId);
    }
}
