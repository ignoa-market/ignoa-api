package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.dto.response.PurchasePreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemQueryRepository;
import io.wisoft.ignoa_api.trade.entity.Trade;
import io.wisoft.ignoa_api.trade.service.TradeReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemQueryService {

    private final ItemPreviewAssembler itemPreviewAssembler;
    private final TradeReader tradeReader;
    private final ItemQueryRepository itemQueryRepository;

    public List<ItemPreview> getMyItems(Long userId) {
        List<Item> items = itemQueryRepository.findItemsBySellerId(userId);
        return itemPreviewAssembler.assemble(userId, items);
    }

    public List<ItemPreview> getMyBidItems(Long userId) {
        List<Item> items = itemQueryRepository.findItemsByBidderId(userId);
        return itemPreviewAssembler.assemble(userId, items);
    }

    public List<PurchasePreview> getMyPurchases(Long userId) {
        List<Trade> trades = tradeReader.findPurchasesByBuyerId(userId);

        List<Item> items = trades.stream()
                .map(Trade::getItem)
                .distinct()
                .toList();

        Map<Long, ItemPreview> itemPreviewMap = itemPreviewAssembler.assemble(userId, items).stream()
                .collect(Collectors.toMap(
                        ItemPreview::itemId,
                        preview -> preview
                ));

        return trades.stream()
                .map(trade -> PurchasePreview.from(
                        trade,
                        itemPreviewMap.get(trade.getItem().getId())
                )).toList();
    }
}
