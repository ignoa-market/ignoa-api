package io.wisoft.ignoa_api.wish.service;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemMediaService;
import io.wisoft.ignoa_api.wish.dto.request.WishListRequest;
import io.wisoft.ignoa_api.wish.dto.response.WishPreview;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishService {

    private final WishRepository wishRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ItemMediaService itemMediaService;

    @Transactional
    public void addWish(Long itemId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));

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

    public SliceResponse<WishPreview> getWishes(Long userId, WishListRequest request) {
        Slice<Wish> wishSlice = wishRepository.findByUserIdWithItem(userId, PageRequest.of(request.page(), request.size()));

        List<WishPreview> wishSummaries = wishSlice.getContent().stream()
                .map(wish -> WishPreview.from(
                        wish,
                        itemMediaService.getFirstMediaUrl(wish.getItem().getId()),
                        wishRepository.countByItemId(wish.getItem().getId())))
                .toList();

        return SliceResponse.of(wishSummaries, wishSlice.hasNext());
    }
}
