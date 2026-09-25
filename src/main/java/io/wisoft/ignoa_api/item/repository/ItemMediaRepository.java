package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemMediaRepository extends JpaRepository<ItemMedia, Long> {

    List<ItemMedia> findAllByItemIdOrderByIdAsc(Long itemId);

    void deleteAllByItemIdAndIdIn(Long itemId, List<Long> ids);

    void deleteAllByItemId(Long itemId);

    @Query("SELECT im FROM ItemMedia im WHERE im.item.id = :itemId")
    List<ItemMedia> findAllByItemId(@Param("itemId") Long itemId);

    List<ItemMedia> findAllByItemIdAndIdIn(Long itemId, List<Long> mediaIds);

    @Query("""
            SELECT m.item.id, m.objectKey
            FROM ItemMedia m
            WHERE m.item.id IN :itemIds
                AND m.mediaType = :mediaType
            ORDER BY m.id ASC
            """)
    List<Object[]> findObjectKeysByItemIds(@Param("itemIds") List<Long> itemIds, @Param("mediaType") ItemMediaType mediaType);
}
