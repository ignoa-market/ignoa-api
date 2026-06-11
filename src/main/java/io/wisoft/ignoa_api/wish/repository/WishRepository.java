package io.wisoft.ignoa_api.wish.repository;

import io.wisoft.ignoa_api.wish.entity.Wish;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishRepository extends JpaRepository<Wish, Long> {

    Optional<Wish> findByUserIdAndItemId(Long userId, Long itemId);

    int countByItemId(Long itemId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    void deleteAllByItemId(Long itemId);

    void deleteAllByUserId(Long userId);

    @Query("SELECT w " +
            "FROM Wish w JOIN FETCH w.item i " +
            "WHERE w.user.id = :userId " +
            "ORDER BY w.createdAt DESC")
    Slice<Wish> findByUserIdWithItem(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT w.item.id, COUNT(w) " +
            "FROM Wish w " +
            "WHERE w.item.id IN :itemIds " +
            "GROUP BY w.item.id")
    List<Object[]> countByItemIds(@Param("itemIds") List<Long> itemIds);

    @Query("""
            SELECT w.item.id
            FROM Wish w
            WHERE w.item.id IN :itemIds
            AND w.user.id = :userId
            """)
    List<Long> findWishedItemIds(@Param("userId") Long userId, @Param("itemIds") List<Long> itemIds);
}
