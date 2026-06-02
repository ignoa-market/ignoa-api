package io.wisoft.ignoa_api.bid.repository;

import io.wisoft.ignoa_api.bid.entity.Bid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("SELECT b FROM Bid b JOIN FETCH b.bidder WHERE b.item.id = :itemId ORDER BY b.price DESC")
    Slice<Bid> findByItemIdWithBidder(Long itemId, Pageable pageable);

    Optional<Bid> findTopByBidderIdAndItemIdOrderByPriceDesc(Long bidderId, Long itemId);

    @Query("SELECT COUNT(DISTINCT b.bidder.id) FROM Bid b WHERE b.item.id = :itemId")
    int countDistinctBidderByItemId(@Param("itemId") Long itemId);

    void deleteAllByItemId(Long itemId);

    Optional<Bid> findTopByItemIdOrderByPriceDesc(Long itemId);

    @Query("SELECT COUNT(b) > 0 FROM Bid b WHERE b.bidder.id = :userId AND b.item.status = 'ACTIVE'")
    boolean existsByBidderIdAndItemActive(@Param("userId") Long userId);

    List<Bid> findByItemId(Long itemId);
}
