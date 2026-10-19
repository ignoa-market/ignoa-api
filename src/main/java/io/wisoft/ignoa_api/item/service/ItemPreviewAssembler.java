package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.wish.repository.dto.WishCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        // 상품별 찜 수 집계 (조회 결과에 없는 상품은 0건)
        Map<Long, Long> wishCountMap = wishRepository.countByItemIds(itemIds).stream()
                .collect(Collectors.toMap(WishCount::getItemId, WishCount::getCount));

        // 로그인 사용자의 찜 여부 조회
        Set<Long> wishedItemIds = userId == null
                ? Set.of()
                : new HashSet<>(wishRepository.findWishedItemIds(userId, itemIds));

        return items.stream()
                .map(item -> ItemPreview.from(
                        item,
                        mediaUrlMap.get(item.getId()),
                        wishCountMap.getOrDefault(item.getId(), 0L).intValue(),
                        wishedItemIds.contains(item.getId())
                ))
                .toList();
    }
}
