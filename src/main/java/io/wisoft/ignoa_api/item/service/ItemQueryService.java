package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewRequest;
import io.wisoft.ignoa_api.item.dto.response.*;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.service.TradeReader;
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
    private final TradeReader tradeReader;

    public ItemDetail getItem(Long itemId, Long userId) {
        Item item = itemReader.getByIdWithSeller(itemId);

        // 판매자 여부
        boolean isSeller = userId != null && item.isSeller(userId);

        // 판매자 프로필
        String profileImageUrl = mediaUrlResolver.toUrl(
                item.getSeller().getProfileImageReference(), item.getSeller().getProfileImageSource()
        );
        SellerProfile sellerProfile = SellerProfile.from(item.getSeller(), profileImageUrl);

        // 상품 이미지
        List<ItemMediaUrls> mediaUrls = itemMediaService.getMediaUrls(itemId);

        // 찜 (개수, 여부)
        int wishCount = wishRepository.countByItemId(itemId);
        boolean isWished = userId != null
                && wishRepository.existsByUserIdAndItemId(userId, itemId);

        return ItemDetail.of(item, mediaUrls, sellerProfile, isSeller, isWished, wishCount);
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

    public List<PurchasePreview> getMyPurchases(Long userId) {
        List<Trade> trades = tradeReader.findPurchasesOfBuyer(userId);
        // 같은 상품에 거래가 여러 건일 수 있어 상품은 한 번씩만 카드로 만들고, 상품 ID로 거래와 짝짓는다
        List<Item> items = trades.stream().map(Trade::getItem).distinct().toList();
        Map<Long, ItemPreview> previewByItemId = buildPreviews(userId, items).stream()
                .collect(Collectors.toMap(ItemPreview::itemId, preview -> preview));

        return trades.stream()
                .map(trade -> new PurchasePreview(
                        trade.getId(),
                        trade.getType(),
                        trade.getStatus(),
                        previewByItemId.get(trade.getItem().getId())
                ))
                .toList();
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
