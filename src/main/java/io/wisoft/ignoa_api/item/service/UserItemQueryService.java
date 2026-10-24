package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.repository.dto.MyBidItem;
import io.wisoft.ignoa_api.bid.service.BidReader;
import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.dto.response.PurchasePreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemQueryRepository;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.service.TradeReader;
import io.wisoft.ignoa_api.user.dto.request.MyBidCursorRequest;
import io.wisoft.ignoa_api.user.dto.request.MyItemCursorRequest;
import io.wisoft.ignoa_api.user.dto.request.MyPurchaseCursorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemQueryService {

    private static final int PAGE_SIZE = 20;

    private final TradeReader tradeReader;
    private final BidReader bidReader;
    private final ItemPreviewAssembler itemPreviewAssembler;
    private final ItemQueryRepository itemQueryRepository;

    public CursorResponse<ItemPreview> getMyItems(Long userId, MyItemCursorRequest request) {
        Slice<Item> items = itemQueryRepository.findSellerItems(
                userId, request.cursor(), PageRequest.of(0, PAGE_SIZE)
        );

        List<Item> content = items.getContent();
        List<ItemPreview> itemPreviews = itemPreviewAssembler.assemble(userId, content);

        Long nextCursor = items.hasNext()
                ? content.getLast().getId()
                : null;

        return CursorResponse.of(itemPreviews, nextCursor, items.hasNext());
    }

    public CursorResponse<ItemPreview> getMyBidItems(Long userId, MyBidCursorRequest request) {
        // 상품별 마지막 입찰 기준으로 최근 입찰 순 정렬과 커서 결정
        Slice<MyBidItem> bidItems = bidReader.findBidderItems(
                userId, request.cursor(), PageRequest.of(0, PAGE_SIZE)
        );

        List<Long> itemIds = bidItems.getContent().stream()
                .map(MyBidItem::itemId)
                .toList();

        // IN 조회는 순서가 보장되지 않아 최근 입찰 순으로 다시 정렬
        Map<Long, Item> itemMap = itemQueryRepository.findAllByIdIn(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, item -> item));

        List<Item> items = itemIds.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .toList();

        List<ItemPreview> itemPreviews = itemPreviewAssembler.assemble(userId, items);

        Long nextCursor = bidItems.hasNext()
                ? bidItems.getContent().getLast().lastBidId()
                : null;

        return CursorResponse.of(itemPreviews, nextCursor, bidItems.hasNext());
    }

    public CursorResponse<PurchasePreview> getMyPurchases(Long userId, MyPurchaseCursorRequest request) {
        Slice<Trade> trades = tradeReader.findBuyerPurchases(
                userId, request.cursor(), PageRequest.of(0, PAGE_SIZE)
        );

        List<Item> items = trades.stream()
                .map(Trade::getItem)
                .distinct()
                .toList();

        Map<Long, ItemPreview> itemPreviewMap = itemPreviewAssembler.assemble(userId, items).stream()
                .collect(Collectors.toMap(
                        ItemPreview::itemId,
                        itemPreview -> itemPreview
                ));

        List<PurchasePreview> purchasePreviews = trades.stream()
                .map(trade -> PurchasePreview.from(
                        trade,
                        itemPreviewMap.get(trade.getItem().getId())
                )).toList();

        Long nextCursor = trades.hasNext()
                ? trades.getContent().getLast().getId()
                : null;

        return CursorResponse.of(purchasePreviews, nextCursor, trades.hasNext());
    }
}
