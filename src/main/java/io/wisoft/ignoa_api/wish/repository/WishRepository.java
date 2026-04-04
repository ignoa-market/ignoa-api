package io.wisoft.ignoa_api.wish.repository;

import io.wisoft.ignoa_api.wish.entity.Wish;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WishRepository extends JpaRepository<Wish, Long> {

    int countByItemId(Long itemId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    Optional<Wish> findByUserIdAndItemId(Long userId, Long itemId);

    @Query("SELECT w FROM Wish w JOIN FETCH w.item i JOIN FETCH i.seller WHERE w.user.id = :userId ORDER BY w.createdAt DESC")
    Slice<Wish> findByUserIdWithItem(@Param("userId") Long userId, Pageable pageable);

    void deleteAllByItemId(Long itemId);

    void deleteAllByUserId(Long userId);
}
