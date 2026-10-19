package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ItemCommandRepository extends JpaRepository<Item, Long> {

    // 입찰가 반영 (현재가 초과, 즉시구매가 미만, 마감 전)
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.currentPrice = :bidPrice,
                i.highestBidder = :highestBidder
            WHERE i.id = :id
                AND i.currentPrice < :bidPrice
                AND i.status IN ('ACTIVE', 'BUY_NOW_PENDING')
                AND i.endAt > :now
                AND i.buyNowPrice > :bidPrice
            """)
    int raiseCurrentPriceIfHigher(@Param("id") Long id, @Param("bidPrice") Long bidPrice,
                                  @Param("highestBidder") User highestBidder, @Param("now") LocalDateTime now);

    // 만료 경매 마감 (최고 입찰자 유무에 따른 상태 변경)
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = CASE WHEN i.highestBidder IS NULL THEN 'NO_BID_CLOSED' ELSE 'BID_CLOSED' END
            WHERE i.id = :id
                AND i.status = 'ACTIVE'
                AND i.endAt <= :now
            """)
    int closeIfExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 입찰 내역 없는 진행 중 상품 삭제
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = 'DELETED'
            WHERE i.id = :id
                AND i.status = 'ACTIVE'
                AND NOT EXISTS (SELECT 1
                                FROM Bid b
                                WHERE b.item.id = :id)
            """)
    int softDeleteIfActive(@Param("id") Long id);

    // 진행 중 경매의 마감 시각 연장 (최대 3회, 회당 1일)
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE items
            SET end_at = DATE_ADD(end_at, INTERVAL 1 DAY),
                extension_count = extension_count + 1
            WHERE id = :id
              AND status = 'ACTIVE'
              AND end_at > :now
              AND extension_count < 3
            """, nativeQuery = true)
    int extendIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

    // 즉시구매 결제 대기 전환 (진행 중, 가격 일치, 마감 전)
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = 'BUY_NOW_PENDING'
            WHERE i.id = :id
                AND i.status = 'ACTIVE'
                AND i.buyNowPrice = :buyNowPrice
                AND i.endAt > :now
            """)
    int reserveBuyNowIfActive(@Param("id") Long id, @Param("buyNowPrice") Long buyNowPrice,
                              @Param("now") LocalDateTime now);

    // 즉시구매 완료 (구매자·거래가 반영, 결제 대기 상태에서만 전환)
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = 'BUY_NOW_CLOSED',
                i.highestBidder = :buyer,
                i.currentPrice = i.buyNowPrice
            WHERE i.id = :id
                AND i.status = 'BUY_NOW_PENDING'
            """)
    int completeBuyNowIfPending(@Param("id") Long id, @Param("buyer") User buyer);

    // 즉시구매 취소 (결제 대기 상태에서 진행 중으로 복구)
    @Modifying
    @Query("""
            UPDATE Item i
            SET i.status = 'ACTIVE'
            WHERE i.id = :id
                AND i.status = 'BUY_NOW_PENDING'
            """)
    int cancelBuyNowIfPending(@Param("id") Long id);
}
