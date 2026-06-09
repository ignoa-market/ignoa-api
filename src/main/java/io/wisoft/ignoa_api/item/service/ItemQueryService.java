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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemQueryService {

    private final ItemMediaService itemMediaService;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public ItemDetail getItem(Long itemId, Long userId) {
        Item item = getItemWithSeller(itemId);

        Optional<Bid> myTopBid = userId != null
                ? bidRepository.findTopByBidderIdAndItemIdOrderByPriceDesc(userId, itemId)
                : Optional.empty();

        boolean isBidder = myTopBid.isPresent();
        boolean isSeller = userId != null && item.isSeller(userId);
        boolean isTopBidder = myTopBid
                .map(bid -> bid.isTopBid(item))
                .orElse(false);

        SellerProfile sellerProfile = SellerProfile.from(item.getSeller());
        List<ItemMediaUrls> mediaUrls = itemMediaService.getMediaUrls(itemId);

        int wishCount = wishRepository.countByItemId(itemId);
        boolean isWished = userId != null && wishRepository.existsByUserIdAndItemId(userId, itemId);

        return ItemDetail.of(
                item, mediaUrls, sellerProfile, isTopBidder, isBidder, isSeller, isWished, wishCount);
    }

    public SliceResponse<ItemPreview> getItems(ItemPreviewRequest request, Long userId) {
        Pageable pageable = PageRequest.of(request.page(), request.size());

        Slice<Item> itemSlice =
                switch (request.view()) {
                    case ALL, LATEST -> itemRepository.findLatestItems(request.category(), pageable);
                    case POPULAR -> itemRepository.findPopularItems(request.category(), pageable);
                    case ENDING_SOON -> itemRepository.findEndingSoonItems(request.category(), pageable);
                };

        List<Long> itemIds = itemSlice.getContent().stream()
                .map(Item::getId).toList();

        Map<Long, String> mediaUrlMap = itemMediaService.getFirstMediaUrlMap(itemIds);
        Map<Long, Integer> wishCountMap = wishRepository.countByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()));
        Set<Long> isWishedSet = (userId == null)
                ? Set.of()
                : new HashSet<>(wishRepository.findWishedItemIds(userId, itemIds));

        List<ItemPreview> itemPreviewList = itemSlice.getContent().stream()
                .map(item -> ItemPreview.from(
                        item,
                        mediaUrlMap.get(item.getId()),
                        wishCountMap.getOrDefault(item.getId(), 0),
                        isWishedSet.contains(item.getId())
                )).toList();

        return SliceResponse.of(itemPreviewList, itemSlice.hasNext());
    }

    public List<ItemPreview> getMyItems(Long userId) {
        return itemRepository.findItemsBySellerId(userId).stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        wishRepository.countByItemId(item.getId()),
                        userId != null && wishRepository.existsByUserIdAndItemId(userId, item.getId())
                )).toList();
    }

    public List<ItemPreview> getMyBids(Long userId) {
        return itemRepository.findItemsByBidderId(userId).stream()
                .map(item -> ItemPreview.from(
                        item,
                        itemMediaService.getFirstMediaUrl(item.getId()),
                        wishRepository.countByItemId(item.getId()),
                        userId != null && wishRepository.existsByUserIdAndItemId(userId, item.getId())
                )).toList();
    }

    public Item getItemWithSeller(Long itemId) {
        return itemRepository.findByIdWithSeller(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    public Item findById(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }
}
