package io.wisoft.ignoa_api.wish.service;

import io.wisoft.ignoa_api.global.dto.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.repository.ProductRepository;
import io.wisoft.ignoa_api.product.service.ProductMediaService;
import io.wisoft.ignoa_api.wish.dto.request.WishListRequest;
import io.wisoft.ignoa_api.wish.dto.response.WishSummary;
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
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMediaService productMediaService;

    @Transactional
    public void addWish(Long productId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (wishRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BusinessException(ErrorCode.WISH_ALREADY_EXISTS);
        }

        wishRepository.save(Wish.create(user, product));
    }

    @Transactional
    public void removeWish(Long productId, Long userId) {
        Wish wish = wishRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));

        wishRepository.delete(wish);
    }

    public SliceResponse<WishSummary> getWishes(Long userId, WishListRequest request) {
        Slice<Wish> wishSlice = wishRepository.findByUserIdWithProduct(userId, PageRequest.of(request.page(), request.size()));

        List<WishSummary> wishSummaries = wishSlice.getContent().stream()
                .map(wish -> WishSummary.from(wish, productMediaService.getFirstMediaUrl(wish.getProduct().getId())))
                .toList();

        return SliceResponse.of(wishSummaries, wishSlice.hasNext());
    }
}
