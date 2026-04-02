package io.wisoft.ignoa_api.product.dto.response;

import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long productId,
        Long sellerId,
        String sellerName,
        String title,
        String description,
        String category,
        Long startPrice,
        Long currentPrice,
        boolean isTopBidder,
        ProductStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Boolean isWished,
        Integer wishCount,
        Integer bidCount,
        List<ProductMediaInfo> mediaUrls
) {
    public static ProductDetailResponse of(
            Product product,
            boolean isTopBidder,
            List<ProductMediaInfo> mediaUrls,
            int wishCount,
            int bidCount,
            boolean isWished
    ) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getName(),
                product.getTitle(),
                product.getDescription(),
                product.getCategory(),
                product.getStartPrice(),
                product.getCurrentPrice(),
                isTopBidder,
                product.getStatus(),
                product.getStartTime(),
                product.getEndTime(),
                isWished,
                wishCount,
                bidCount,
                mediaUrls
        );
    }
}
