package io.wisoft.ignoa_api.product.repository;

import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductStatus;
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

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p " +
            "WHERE p.status = 'ON_SALE' " +
            "AND (:category IS NULL OR p.category = :category) " +
            "ORDER BY (SELECT COUNT(w) " +
            "          FROM Wish w " +
            "          WHERE w.product = p) " +
            "          DESC, p.createdAt DESC")
    Slice<Product> findPopularProducts(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "WHERE p.status = 'ON_SALE' " +
            "AND (:category IS NULL OR p.category = :category) " +
            "ORDER BY p.endTime ASC")
    Slice<Product> findEndingSoonProducts(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "WHERE p.status = 'ON_SALE' " +
            "AND (:category IS NULL OR p.category = :category) " +
            "ORDER BY p.createdAt DESC")
    Slice<Product> findLatestProducts(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "WHERE p.seller.id = :userId " +
            "AND (:category IS NULL OR p.category = :category) " +
            "ORDER BY p.createdAt DESC")
    Slice<Product> findMyProducts(@Param("category") String category, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p " +
            "JOIN Bid b ON b.product = p " +
            "WHERE b.bidder.id = :userId " +
            "AND p.status = 'ON_SALE' " +
            "AND (:category IS NULL OR p.category = :category) " +
            "ORDER BY p.createdAt DESC")
    Slice<Product> findMyBidProducts(@Param("category") String category, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.seller " +
            "WHERE p.id = :productId")
    Optional<Product> findByIdWithSeller(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    List<Product> findAllByStatusAndEndTimeBefore(ProductStatus status, LocalDateTime endTimeBefore);

    boolean existsBySellerIdAndStatus(Long userId, ProductStatus status);
}
