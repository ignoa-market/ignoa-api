package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewRequest;
import io.wisoft.ignoa_api.item.dto.response.*;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.wish.repository.dto.WishCount;
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
    private final ItemReader itemReader;
    private final MediaUrlResolver mediaUrlResolver;

    private final ItemRepository itemRepository;
    private final WishRepository wishRepository;
    private final BidRepository bidRepository;

    public ItemDetail getItem(Long itemId, Long userId) {
        Item item = itemReader.getByIdWithSeller(itemId);

        Optional<Bid> myTopBid = userId != null
                ? bidRepository.findTopByBidderIdAndItemIdOrderByPriceDesc(userId, itemId)
                : Optional.empty();

        boolean isBidder = myTopBid.isPresent();
        boolean isSeller = userId != null && item.isSeller(userId);
        boolean isTopBidder = myTopBid
                .map(bid -> bid.isTopBid(item))
                .orElse(false);

        String profileImageUrl = mediaUrlResolver.toUrl(item.getSeller().getProfileImageReference(), item.getSeller().getProfileImageSource());
        SellerProfile sellerProfile = SellerProfile.from(item.getSeller(), profileImageUrl);
        List<ItemMediaUrls> mediaUrls = itemMediaService.getMediaUrls(itemId);

        int wishCount = wishRepository.countByItemId(itemId);
        boolean isWished = userId != null && wishRepository.existsByUserIdAndItemId(userId, itemId);

        return ItemDetail.of(item, mediaUrls, sellerProfile, isTopBidder, isBidder, isSeller, isWished, wishCount);
    }

    public SliceResponse<ItemPreview> getItems(ItemPreviewRequest request, Long userId) {
        Pageable pageable = PageRequest.of(request.page(), request.size());
        Slice<Item> itemSlice = getItemsByView(request, pageable);
        List<ItemPreview> itemPreviews = buildPreviews(userId, itemSlice.getContent());

        return SliceResponse.of(itemPreviews, itemSlice.hasNext());
    }

    public List<ItemPreview> getMyItems(Long userId) {
        List<Item> items = itemRepository.findItemsBySellerId(userId);
        return buildPreviews(userId, items);
    }

    public List<ItemPreview> getMyBidItems(Long userId) {
        List<Item> items = itemRepository.findItemsByBidderId(userId);
        return buildPreviews(userId, items);
    }

    private List<ItemPreview> buildPreviews(Long userId, List<Item> items) {
        List<Long> itemIds = items.stream()
                .map(Item::getId)
                .toList();

        Map<Long, String> mediaUrlMap = itemMediaService.getFirstMediaUrl(itemIds);
        Map<Long, Long> wishCountMap = wishRepository.countByItemIds(itemIds).stream()
                .collect(Collectors.toMap(WishCount::getItemId, WishCount::getCount));
        Set<Long> wishedItemIdSet = userId == null
                ? Set.of()
                : new HashSet<>(wishRepository.findWishedItemIds(userId, itemIds));

        return items.stream()
                .map(item -> ItemPreview.from(
                        item,
                        mediaUrlMap.get(item.getId()),
                        wishCountMap.getOrDefault(item.getId(), 0L).intValue(),
                        wishedItemIdSet.contains(item.getId())
                )).toList();
    }

    private Slice<Item> getItemsByView(ItemPreviewRequest request, Pageable pageable) {
        return switch (request.view()) {
            case ALL, LATEST -> itemRepository.findLatestItems(request.category(), pageable);
            case POPULAR -> itemRepository.findPopularItems(request.category(), pageable);
            case ENDING_SOON -> itemRepository.findEndingSoonItems(request.category(), pageable);
        };
    }
}
