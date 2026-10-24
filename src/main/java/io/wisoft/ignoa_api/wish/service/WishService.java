package io.wisoft.ignoa_api.wish.service;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemCommandRepository;
import io.wisoft.ignoa_api.item.service.ItemMediaService;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import io.wisoft.ignoa_api.wish.dto.request.WishPreviewRequest;
import io.wisoft.ignoa_api.wish.dto.response.WishPreview;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishService {

    private final UserQueryService userQueryService;
    private final ItemMediaService itemMediaService;
    private final ItemReader itemReader;
    private final WishRepository wishRepository;
    private final ItemCommandRepository itemCommandRepository;

    @Transactional
    public void addWish(Long itemId, Long userId) {
        User user = userQueryService.findById(userId);
        Item item = itemReader.getById(itemId);

        if (wishRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
        }

        itemCommandRepository.increaseWishCount(itemId);
        wishRepository.save(Wish.create(user, item));
    }

    @Transactional
    public void removeWish(Long itemId, Long userId) {
        itemCommandRepository.decreaseWishCount(itemId);

        int deletedRows = wishRepository.deleteByItemIdAndUserId(itemId, userId);

        if (deletedRows == 0) {
            throw new BusinessException(ErrorCode.WISH_NOT_FOUND);
        }
    }

    public SliceResponse<WishPreview> getWishes(Long userId, WishPreviewRequest request) {
        PageRequest pageable = PageRequest.of(request.page(), request.size());

        // 찜 목록 조회 (Offset 방식, 찜이 많아지면 Cursor 방식으로 개선)
        Slice<Wish> wishes = wishRepository.findByUserIdWithItem(userId, pageable);

        if (wishes.isEmpty()) {
            return SliceResponse.of(List.of(), wishes.hasNext());
        }

        List<Long> itemIds = wishes.getContent().stream()
                .map(wish -> wish.getItem().getId())
                .toList();

        Map<Long, String> mediaUrlMap = itemMediaService.getFirstMediaUrl(itemIds);

        List<WishPreview> wishPreview = wishes.getContent().stream()
                .map(wish -> WishPreview.from(
                        wish,
                        mediaUrlMap.get(wish.getItem().getId()),
                        wish.getItem().getWishCount(),
                        wish.getItem().getStatus()
                )).toList();

        return SliceResponse.of(wishPreview, wishes.hasNext());
    }
}
