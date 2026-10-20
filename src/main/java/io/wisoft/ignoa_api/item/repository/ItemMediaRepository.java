package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemMediaRepository extends JpaRepository<ItemMedia, Long> {

    // 상품별 미디어 조회 (ID 오름차순)
    List<ItemMedia> findAllByItemIdOrderByIdAsc(Long itemId);

    // 상품별 전체 미디어 조회
    @Query("""
            SELECT im FROM ItemMedia im
            WHERE im.item.id = :itemId
            """)
    List<ItemMedia> findAllByItemId(@Param("itemId") Long itemId);

    // 상품에 속한 지정 미디어 조회
    List<ItemMedia> findAllByItemIdAndIdIn(Long itemId, List<Long> mediaIds);

    // 상품 목록의 유형별 상품 ID·오브젝트 키 조회 (미디어 ID 오름차순)
    @Query("""
            SELECT im.item.id, im.objectKey
            FROM ItemMedia im
            WHERE im.item.id IN :itemIds
                AND im.mediaType = :mediaType
            ORDER BY im.id ASC
            """)
    List<Object[]> findObjectKeysByItemIdsAndMediaType(@Param("itemIds") List<Long> itemIds,
                                                       @Param("mediaType") ItemMediaType mediaType);

    // 상품에 속한 지정 미디어 삭제
    void deleteAllByItemIdAndIdIn(Long itemId, List<Long> mediaIds);

    // 상품별 전체 미디어 삭제
    @Modifying
    @Query("""
            DELETE FROM ItemMedia im
            WHERE im.item.id = :itemId
            """)
    void deleteAllByItemId(@Param("itemId") Long itemId);
}
