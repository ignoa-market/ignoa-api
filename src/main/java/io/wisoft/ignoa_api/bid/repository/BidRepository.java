package io.wisoft.ignoa_api.bid.repository;

import io.wisoft.ignoa_api.bid.entity.Bid;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByItemId(Long itemId);

    boolean existsByItemId(Long itemId);

    Optional<Bid> findTopByBidderIdAndItemIdOrderByPriceDesc(Long bidderId, Long itemId);

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
            WHERE b.bidder.id = :userId
                AND b.item.status = 'ACTIVE'
            """)
    boolean existsByBidderIdAndItemActive(@Param("userId") Long userId);

    // 경매 마감 - 해당 경매의 활성 입찰을 모두 패찰 처리
    @Modifying
    @Query("""
            UPDATE Bid b
            SET b.status = 'LOST'
            WHERE b.item.id = :itemId
              AND b.status = 'ACTIVE'
            """)
    int markLosingBids(@Param("itemId") Long itemId);

    // 경매 마감 - 해당 경매의 최고가 입찰을 1건을 낙찰 처리
    @Modifying
    @Query(value = """
            UPDATE bids
            SET status = 'WON'
            WHERE item_id = :itemId
             AND status = 'ACTIVE'
            ORDER BY price DESC, id DESC
            LIMIT 1
            """, nativeQuery = true)
    int markWinningBid(@Param("itemId") Long itemId);
}
