package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Query("SELECT i FROM Item i " +
            "WHERE i.status = 'ACTIVE' " +
            "AND (:category IS NULL OR i.category = :category) " +
            "ORDER BY (SELECT COUNT(w) " +
            "          FROM Wish w " +
            "          WHERE w.item = i) " +
            "          DESC, i.createdAt DESC")
    Slice<Item> findPopularItems(@Param("category") String category, Pageable pageable);

    @Query("SELECT i FROM Item i " +
            "WHERE i.status = 'ACTIVE' " +
            "AND (:category IS NULL OR i.category = :category) " +
            "ORDER BY i.endAt ASC")
    Slice<Item> findEndingSoonItems(@Param("category") String category, Pageable pageable);

    @Query("SELECT i FROM Item i " +
            "WHERE i.status = 'ACTIVE' " +
            "AND (:category IS NULL OR i.category = :category) " +
            "ORDER BY i.createdAt DESC")
    Slice<Item> findLatestItems(@Param("category") String category, Pageable pageable);

    @Query("SELECT i FROM Item i " +
            "WHERE i.seller.id = :userId " +
            "AND (:category IS NULL OR i.category = :category) " +
            "ORDER BY i.createdAt DESC")
    Slice<Item> findMyItems(@Param("category") String category, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Item i " +
            "JOIN Bid b ON b.item = i " +
            "WHERE b.bidder.id = :userId " +
            "AND i.status = 'ACTIVE' " +
            "AND (:category IS NULL OR i.category = :category) " +
            "ORDER BY i.createdAt DESC")
    Slice<Item> findMyBidItems(@Param("category") String category, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT i FROM Item i " +
            "JOIN FETCH i.seller " +
            "WHERE i.id = :itemId")
    Optional<Item> findByIdWithSeller(@Param("itemId") Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdWithLock(@Param("id") Long id);

    List<Item> findAllByStatusAndEndAtBefore(ItemStatus status, LocalDateTime endAtBefore);

    boolean existsBySellerIdAndStatus(Long userId, ItemStatus status);
}
