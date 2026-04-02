package io.wisoft.ignoa_api.product.service;

import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.auction.service.AuctionRedisService;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.dto.SliceResponse;
import io.wisoft.ignoa_api.product.dto.request.ProductCreateRequest;
import io.wisoft.ignoa_api.product.dto.request.ProductListRequest;
import io.wisoft.ignoa_api.product.dto.request.ProductUpdateRequest;
import io.wisoft.ignoa_api.product.dto.response.*;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.repository.ProductRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductMediaService productMediaService;
    private final AuctionRedisService auctionRedisService;
    private final ApplicationEventPublisher eventPublisher;

    private final ProductRepository productRepository;
    private final WishRepository wishRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;

    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request, List<MultipartFile> files) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = Product.create(
                seller,
                request.title(),
                request.description(),
                request.category(),
                request.startPrice(),
                request.endTime()
        );

        productRepository.save(product);
        productMediaService.saveMedia(product, files);
        eventPublisher.publishEvent(new AuctionRegisteredEvent(product.getId(), product.getEndTime()));

        return new ProductResponse(product.getId());
    }

    public SliceResponse<ProductSummary> getProducts(Long userId, ProductListRequest request) {
        Slice<Product> productSlice = getProductsByView(request, userId, PageRequest.of(request.page(), request.size()));

        List<ProductSummary> productSummaries = productSlice.getContent().stream()
                .map(product -> ProductSummary.from(
                        product,
                        productMediaService.getFirstMediaUrl(product.getId()),
                        getWishCount(product.getId()),
                        getBidCount(product.getId())
                )).toList();

        return SliceResponse.of(productSummaries, productSlice.hasNext());
    }

    private Slice<Product> getProductsByView(ProductListRequest request, Long userId, Pageable pageable) {
        if (request.view().requiresAuth() && userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return switch (request.view()) {
            case ALL, LATEST -> productRepository.findLatestProducts(request.category(), pageable);
            case POPULAR -> productRepository.findPopularProducts(request.category(), pageable);
            case ENDING_SOON -> productRepository.findEndingSoonProducts(request.category(), pageable);
            case MY_PRODUCTS -> productRepository.findMyProducts(request.category(), userId, pageable);
            case MY_BIDS -> productRepository.findMyBidProducts(request.category(), userId, pageable);
        };
    }

    private int getWishCount(Long productId) {
        return wishRepository.countByProductId(productId);
    }

    private int getBidCount(Long productId) {
        return bidRepository.countDistinctBidderByProductId(productId);
    }

    public ProductDetailResponse getProductDetail(Long productId, Long userId) {
        Product product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        boolean isTopBidder = bidRepository.findTopByBidderIdAndProductIdOrderByPriceDesc(userId, productId)
                .map(bid -> bid.getPrice().equals(product.getCurrentPrice()))
                .orElse(false);
        List<ProductMediaInfo> mediaUrls = productMediaService.getMediaInfoByProductId(productId);
        int wishCount = getWishCount(productId);
        int bidCount = getBidCount(productId);
        boolean isWished = wishRepository.existsByUserIdAndProductId(userId, productId);

        return ProductDetailResponse.of(product, isTopBidder, mediaUrls, wishCount, bidCount, isWished);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long productId, Long userId, ProductUpdateRequest request, List<MultipartFile> files) {
        Product product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isSeller(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_UPDATE_FORBIDDEN);
        }

        if (product.isClosed()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
        }

        product.updateInfo(request.title(), request.description(), request.category(), request.endTime());

        if (request.deleteMediaIds() != null && !request.deleteMediaIds().isEmpty()) {
            productMediaService.validateMinimumMediaCount(productId, request.deleteMediaIds(), files);
            productMediaService.deleteMediaByIds(productId, request.deleteMediaIds());
        }

        if (files != null && !files.isEmpty()) {
            productMediaService.saveMedia(product, files);
        }

        eventPublisher.publishEvent(new AuctionRegisteredEvent(product.getId(), product.getEndTime()));

        return getProductDetail(productId, userId);
    }

    @Transactional
    public ProductResponse deleteProduct(Long productId, Long userId) {
        Product product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isSeller(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_DELETE_FORBIDDEN);
        }

        productMediaService.deleteAllByProductId(productId);
        bidRepository.deleteAllByProductId(productId);
        wishRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
        auctionRedisService.deleteTtl(productId);

        return new ProductResponse(product.getId());
    }
}
