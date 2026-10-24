package io.wisoft.ignoa_api.bid.repository;

import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.repository.dto.MyBidItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByItemId(Long itemId);

    boolean existsByItemId(Long itemId);

    @Query("""
            SELECT COUNT(b) > 0
            FROM Bid b 
            WHERE b.bidder.id = :userId
                AND b.item.status IN ('ACTIVE', 'BUY_NOW_RESERVED')
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

    // 입찰 내역 - 커서(입찰 ID) 이전 입찰을 최신순 조회 (현재가보다 높을 때만 저장되므로 ID순 = 가격순)
    @Query("""
            SELECT b
            FROM Bid b
            JOIN FETCH b.bidder
            WHERE b.item.id = :itemId
                AND (:cursor IS NULL OR b.id < :cursor)
            ORDER BY b.id DESC
            """)
    Slice<Bid> findBidHistory(@Param("itemId") Long itemId, @Param("cursor") Long cursor, Pageable pageable);

    // 입찰자별 입찰 진행 중 입찰 상품 조회 (상품별 마지막 입찰 ID 기준, 최근 입찰 순)
    @Query("""
            SELECT b.item.id, MAX(b.id)
            FROM Bid b
            WHERE b.bidder.id = :userId
                AND b.item.status IN ('ACTIVE', 'BUY_NOW_RESERVED')
            GROUP BY b.item.id
            HAVING (:cursor IS NULL OR MAX(b.id) < :cursor)
            ORDER BY MAX(b.id) DESC
            """)
    Slice<MyBidItem> findBidderItems(@Param("userId") Long userId, @Param("cursor") Long cursor,
                                     Pageable pageable);
}
