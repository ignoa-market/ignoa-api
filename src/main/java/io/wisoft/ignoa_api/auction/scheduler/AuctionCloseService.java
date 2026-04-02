package io.wisoft.ignoa_api.auction.scheduler;

import io.wisoft.ignoa_api.auction.service.AuctionCloseManager;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductStatus;
import io.wisoft.ignoa_api.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionCloseService {

    private final AuctionCloseManager auctionCloseManager;
    private final ProductRepository productRepository;

    public void closeExpiredBids() {
        List<Product> expiredProducts = productRepository.findAllByStatusAndEndTimeBefore(
                ProductStatus.ON_SALE, LocalDateTime.now()
        );

        for (Product product : expiredProducts) {
            try {
                auctionCloseManager.closeAuction(product.getId());
            } catch (Exception e) {
                log.error("경매 마감 처리 실패 productId={}", product.getId(), e);
            }
        }
    }
}
