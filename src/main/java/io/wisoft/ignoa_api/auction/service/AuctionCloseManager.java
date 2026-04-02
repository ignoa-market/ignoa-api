package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductStatus;
import io.wisoft.ignoa_api.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionCloseManager {

    private final BidRepository bidRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long productId) {
        Product lockedProduct = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (lockedProduct.getStatus() != ProductStatus.ON_SALE) {
            log.info("[중복 마감 방지] 이미 마감된 경매 productId={} status={}", productId, lockedProduct.getStatus());
            return;
        }

        Optional<Bid> highestBid = bidRepository.findTopByProductIdOrderByPriceDesc(productId);

        if (highestBid.isEmpty()) {
            lockedProduct.closeAsFailed();
            return;
        }

        Bid winningBid = highestBid.get();
        lockedProduct.closeWithWinner(winningBid.getBidder());
        winningBid.closeAsWon();
    }
}
