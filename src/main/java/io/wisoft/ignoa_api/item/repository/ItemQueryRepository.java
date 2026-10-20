package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.dto.ExpiredItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ItemQueryRepository extends Repository<Item, Long> {

    // 최신순 상품 조회 (진행 중, 커서 ID 미만, ID 내림차순)
    @Query("""
            SELECT i FROM Item i
            WHERE i.status = 'ACTIVE'
                AND (:cursor IS NULL OR i.id < :cursor)
            ORDER BY i.id DESC
            """)
    Slice<Item> findLatestItems(@Param("cursor") Long cursor, Pageable pageable);

    // 마감 임박순 상품 조회 (진행 중, 마감 시각·ID 오름차순)
    @Query("""
            SELECT i FROM Item i
            WHERE i.status = 'ACTIVE'
            ORDER BY i.endAt ASC, i.id ASC
            """)
    Slice<Item> findEndingSoonItems(Pageable pageable);

    // 인기순 상품 조회 (진행 중, 고유 입찰자 수·찜 수·ID 내림차순)
    @Query("""
            SELECT i FROM Item i
            WHERE i.status = 'ACTIVE'
            ORDER BY
                (SELECT COUNT(DISTINCT b.bidder.id) FROM Bid b WHERE b.item = i) DESC,
                (SELECT COUNT(w) FROM Wish w WHERE w.item = i) DESC,
                i.id DESC
            """)
    Slice<Item> findPopularItems(Pageable pageable);

    // 판매자별 등록 상품 조회 (등록 시각 내림차순)
    @Query("""
            SELECT i FROM Item i
            WHERE i.seller.id = :sellerId
            ORDER BY i.createdAt DESC
            """)
    List<Item> findItemsBySellerId(@Param("sellerId") Long sellerId);

    // 사용자별 입찰 참여 상품 조회 (중복 제외, 등록 시각 내림차순)
    @Query("""
            SELECT DISTINCT i FROM Item i
            JOIN Bid b ON b.item = i
            WHERE b.bidder.id = :bidderId
            ORDER BY i.createdAt DESC
            """)
    List<Item> findItemsByBidderId(@Param("bidderId") Long bidderId);

    // 만료된 진행 중 상품의 ID·마감 시각 조회 (마감 시각·ID 오름차순)
    @Query("""
            SELECT i.id, i.endAt
            FROM Item i
            WHERE i.status = 'ACTIVE'
                AND i.endAt <= :now
            ORDER BY i.endAt ASC,i.id ASC
            """)
    List<ExpiredItem> findExpiredActiveItems(@Param("now") LocalDateTime now, Pageable pageable);

    // 마감 처리되지 않은 만료 상품 수 집계
    @Query("""
            SELECT COUNT(i)
            FROM Item i
            WHERE i.status = 'ACTIVE'
                AND i.endAt <= :now
            """)
    long countExpiredActiveItems(@Param("now") LocalDateTime now);
}
