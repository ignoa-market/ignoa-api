package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.dto.response.PurchasePreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
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
    private final ItemRepository itemRepository;

    public List<ItemPreview> getMyItems(Long userId) {
        List<Item> items = itemRepository.findItemsBySellerId(userId);
        return itemPreviewAssembler.assemble(userId, items);
    }

    public List<ItemPreview> getMyBidItems(Long userId) {
        List<Item> items = itemRepository.findItemsByBidderId(userId);
        return itemPreviewAssembler.assemble(userId, items);
    }

    public List<PurchasePreview> getMyPurchases(Long userId) {
        List<Trade> trades = tradeReader.findPurchasesOfBuyer(userId);
        // 같은 상품에 거래가 여러 건일 수 있어 상품은 한 번씩만 카드로 만들고, 상품 ID로 거래와 짝짓는다
        List<Item> items = trades.stream().map(Trade::getItem).distinct().toList();
        Map<Long, ItemPreview> previewByItemId = itemPreviewAssembler.assemble(userId, items).stream()
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
}
