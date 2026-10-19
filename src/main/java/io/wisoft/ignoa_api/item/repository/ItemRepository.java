package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // 판매자 정보를 포함한 상품 단건 조회
    @Query("""
            SELECT i FROM Item i
            JOIN FETCH i.seller
            WHERE i.id = :itemId
            """)
    Optional<Item> findByIdWithSeller(@Param("itemId") Long itemId);

    // 판매자별 지정 상태의 상품 존재 여부 확인
    boolean existsBySellerIdAndStatusIn(Long userId, List<ItemStatus> statuses);

    // 상품 수정용 단건 조회 및 비관적 쓰기 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findForUpdateById(Long itemId);
}
