package io.wisoft.ignoa_api.wish.repository;

import io.wisoft.ignoa_api.wish.entity.Wish;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishRepository extends JpaRepository<Wish, Long> {

    int countByProductId(Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<Wish> findByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT w FROM Wish w JOIN FETCH w.product p JOIN FETCH p.seller WHERE w.user.id = :userId ORDER BY w.createdAt DESC")
    Slice<Wish> findByUserIdWithProduct(@Param("userId") Long userId, Pageable pageable);

    void deleteAllByProductId(Long productId);

    void deleteAllByUserId(Long userId);
}
