package io.wisoft.ignoa_api.product.dto.response;

import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductStatus;

import java.time.LocalDateTime;

public record ProductSummary(
        Long productId,
        String title,
        String mediaUrl,
        Long currentPrice,
        Integer wishCount,
        Integer bidCount,
        ProductStatus status,
        LocalDateTime endTime
) {

    public static ProductSummary from(Product product, String mediaUrl, int wishCount, int bidCount) {
        return new ProductSummary(
                product.getId(),
                product.getTitle(),
                mediaUrl,
                product.getCurrentPrice(),
                wishCount,
                bidCount,
                product.getStatus(),
                product.getEndTime()
        );
    }
}
