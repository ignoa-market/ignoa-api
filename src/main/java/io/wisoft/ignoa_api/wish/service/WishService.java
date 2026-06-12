package io.wisoft.ignoa_api.wish.service;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemMediaService;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import io.wisoft.ignoa_api.wish.dto.request.WishPreviewRequest;
import io.wisoft.ignoa_api.wish.dto.response.WishPreview;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.wish.repository.dto.WishCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishService {

    private final UserQueryService userQueryService;
    private final ItemMediaService itemMediaService;
    private final ItemReader itemReader;
    private final WishRepository wishRepository;

    @Transactional
    public void addWish(Long itemId, Long userId) {
        User user = userQueryService.findById(userId);
        Item item = itemReader.getById(itemId);

        if (wishRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
        }

        wishRepository.save(Wish.create(user, item));
    }

    @Transactional
    public void removeWish(Long itemId, Long userId) {
        Wish wish = wishRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

        wishRepository.delete(wish);
    }

    public SliceResponse<WishPreview> getWishes(Long userId, WishPreviewRequest request) {
        Slice<Wish> wishSlice = wishRepository
                .findByUserIdWithItem(userId, PageRequest.of(request.page(), request.size()));

        if (wishSlice.isEmpty()) {
            return SliceResponse.of(List.of(), wishSlice.hasNext());
        }

        List<Long> itemIds = wishSlice.getContent().stream()
                .map(wish -> wish.getItem().getId())
                .toList();

        Map<Long, String> mediaUrlMap = itemMediaService.getFirstMediaUrl(itemIds);
        Map<Long, Long> wishCountMap = wishRepository.countByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        WishCount::getItemId,
                        WishCount::getCount
                ));

        List<WishPreview> wishPreview = wishSlice.getContent().stream()
                .map(wish -> WishPreview.from(
                        wish,
                        mediaUrlMap.get(wish.getItem().getId()),
                        wishCountMap.get(wish.getItem().getId()).intValue(),
                        wish.getItem().getStatus()
                )).toList();

        return SliceResponse.of(wishPreview, wishSlice.hasNext());
    }
}
