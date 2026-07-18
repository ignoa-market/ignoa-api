package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("""
            SELECT i FROM Item i
            LEFT JOIN Wish w ON w.item = i
            WHERE i.status = 'ACTIVE'
            AND (:category IS NULL OR i.category = :category)
            GROUP BY i
            ORDER BY COUNT(w) DESC, i.createdAt DESC, i.id DESC
            """)
    Slice<Item> findPopularItems(@Param("category") String category, Pageable pageable);

    @Query("""
            SELECT i FROM Item i
            WHERE i.status = 'ACTIVE'
            AND (:category IS NULL OR i.category = :category)
            ORDER BY i.endAt ASC, i.id ASC
            """)
    Slice<Item> findEndingSoonItems(@Param("category") String category, Pageable pageable);

    @Query("""
            SELECT i FROM Item i
            WHERE i.status = 'ACTIVE'
            AND (:category IS NULL OR i.category = :category)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    Slice<Item> findLatestItems(@Param("category") String category, Pageable pageable);

    @Query("""
            SELECT i FROM Item i
            WHERE i.seller.id = :sellerId
            ORDER BY i.createdAt DESC
            """)
    List<Item> findItemsBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT DISTINCT i FROM Item i
            JOIN Bid b ON b.item = i
            WHERE b.bidder.id = :bidderId
            ORDER BY i.createdAt DESC
            """)
    List<Item> findItemsByBidderId(@Param("bidderId") Long bidderId);

    @Query("""
            SELECT i FROM Item i
            JOIN FETCH i.seller
            WHERE i.id = :itemId
            """)
    Optional<Item> findByIdWithSeller(@Param("itemId") Long itemId);

    List<Item> findAllByStatusAndEndAtBefore(ItemStatus status, LocalDateTime endAtBefore);

    boolean existsBySellerIdAndStatus(Long userId, ItemStatus status);

    @Modifying
    @Query("""
            UPDATE Item i
            SET i.currentPrice = :price,
                i.highestBidder = :highestBidder,
                i.version = i.version + 1
            WHERE i.id = :id
                AND i.currentPrice < :price
                AND i.status = 'ACTIVE'
            """)
    int raiseCurrentPriceIfHigher(@Param("id") Long id, @Param("price") Long price, @Param("highestBidder") User highestBidder);

    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = 'BUY_NOW_CLOSED',
                i.highestBidder = :buyer,
                i.version = i.version + 1
            WHERE i.id = :id
                AND i.status = 'ACTIVE'
                AND i.buyNowPrice = :buyNowPrice
            """)
    int buyNowIfActive(@Param("id") Long id, @Param("buyer") User buyer, @Param("buyNowPrice") Long buyNowPrice);

    @Modifying
    @Query("""
            UPDATE Item i 
            SET i.status = CASE WHEN i.highestBidder IS NULL THEN 'NO_BID_CLOSED' ELSE 'BID_CLOSED' END,
                i.version = i.version + 1
            WHERE i.id = :id
                AND i.status = 'ACTIVE'                                    
            """)
    int closeIfActive(@Param("id") Long id);
}
