package io.wisoft.ignoa_api.bid.repository;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.entity.BidStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.product.id = :productId ORDER BY b.price DESC")
    Slice<Bid> findByProductIdWithBidder(Long productId, Pageable pageable);

    Optional<Bid> findTopByBidderIdAndProductIdOrderByPriceDesc(Long bidderId, Long productId);

    @Query("SELECT COUNT(DISTINCT b.bidder.id) FROM Bid b WHERE b.product.id = :productId")
    int countDistinctBidderByProductId(@Param("productId") Long productId);

    void deleteAllByProductId(Long productId);

    Optional<Bid> findTopByProductIdOrderByPriceDesc(Long productId);

    @Query("SELECT COUNT(b) > 0 FROM Bid b WHERE b.bidder.id = :userId AND b.product.status = 'ON_SALE'")
    boolean existsByBidderIdAndProductOnSale(@Param("userId") Long userId);
}
