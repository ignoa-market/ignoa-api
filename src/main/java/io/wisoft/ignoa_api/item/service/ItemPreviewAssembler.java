package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ItemPreviewAssembler {

    private final ItemMediaService itemMediaService;
    private final WishRepository wishRepository;

    public List<ItemPreview> assemble(Long userId, List<Item> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = items.stream()
                .map(Item::getId)
                .toList();

        // 대표 이미지
        Map<Long, String> mediaUrlMap = itemMediaService.getFirstMediaUrl(itemIds);

        // 로그인 사용자의 찜 여부 조회
        Set<Long> wishedItemIds = userId == null
                ? Set.of()
                : new HashSet<>(wishRepository.findWishedItemIds(userId, itemIds));

        return items.stream()
                .map(item -> ItemPreview.from(
                        item,
                        mediaUrlMap.get(item.getId()),
                        item.getWishCount(),
                        wishedItemIds.contains(item.getId())
                ))
                .toList();
    }

    public List<ItemPreview> applyWished(Long userId, List<ItemPreview> previews) {
        if (userId == null || previews.isEmpty()) {
            return previews;
        }

        List<Long> itemIds = previews.stream()
                .map(ItemPreview::itemId)
                .toList();

        Set<Long> wishedItemIds = new HashSet<>(wishRepository.findWishedItemIds(userId, itemIds));

        return previews.stream().map(
                preview -> preview.withIsWished(wishedItemIds.contains(preview.itemId()))
        ).toList();
    }
}
