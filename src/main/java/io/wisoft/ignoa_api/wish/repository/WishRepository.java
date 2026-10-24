package io.wisoft.ignoa_api.wish.repository;

import io.wisoft.ignoa_api.wish.entity.Wish;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishRepository extends JpaRepository<Wish, Long> {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    void deleteAllByUserId(Long userId);

    @Modifying
    @Query("""
            DELETE FROM Wish w
            WHERE w.item.id = :itemId
            """)
    void deleteAllByItemId(@Param("itemId") Long itemId);

    @Query("""
            SELECT w
            FROM Wish w
                JOIN FETCH w.item i
            WHERE w.user.id = :userId
                AND (:cursor IS NULL OR w.id < :cursor)
            ORDER BY w.id DESC
            """)
    Slice<Wish> findByUserIdWithItem(@Param("userId") Long userId, @Param("cursor") Long cursor,
                                     Pageable pageable);

    @Query("""
            SELECT w.item.id
            FROM Wish w
            WHERE w.item.id IN :itemIds
            AND w.user.id = :userId
            """)
    List<Long> findWishedItemIds(@Param("userId") Long userId, @Param("itemIds") List<Long> itemIds);

    // 사용자·상품별 찜 삭제
    @Modifying
    @Query("""
            DELETE FROM Wish w
            WHERE w.item.id = :itemId
                AND w.user.id = :userId
            """)
    int deleteByItemIdAndUserId(@Param("itemId") Long itemId, @Param("userId") Long userId);
}
