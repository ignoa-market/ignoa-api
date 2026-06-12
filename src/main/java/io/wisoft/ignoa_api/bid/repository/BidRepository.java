package io.wisoft.ignoa_api.bid.repository;

import io.wisoft.ignoa_api.bid.entity.Bid;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            SELECT b
            FROM Bid b
            JOIN FETCH b.bidder
            WHERE b.item.id = :itemId
            ORDER BY b.price DESC
            """)
    List<Bid> findByItemIdWithBidder(Long itemId);

    @Query("""
            SELECT COUNT(b) > 0 
            FROM Bid b 
            WHERE b.bidder.id = :userId AND b.item.status = 'ACTIVE'
            """)
    boolean existsByBidderIdAndItemActive(@Param("userId") Long userId);

    Optional<Bid> findTopByBidderIdAndItemIdOrderByPriceDesc(Long bidderId, Long itemId);

//    @Query("""
//          SELECT b FROM Bid b
//          JOIN FETCH b.bidder
//          WHERE b.item.id = :itemId
//          ORDER BY b.price DESC
//          LIMIT 1
//          """)
    @EntityGraph(attributePaths = "bidder")
    Optional<Bid> findTopByItemIdOrderByPriceDesc(Long itemId);

    List<Bid> findByItemId(Long itemId);

    boolean existsByItemId(Long itemId);
}
