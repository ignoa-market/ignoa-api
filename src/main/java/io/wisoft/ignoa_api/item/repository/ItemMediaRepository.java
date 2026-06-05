package io.wisoft.ignoa_api.item.repository;

import io.wisoft.ignoa_api.item.dto.response.ItemMediaResponse;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemMediaRepository extends JpaRepository<ItemMedia, Long> {

    Optional<ItemMedia> findFirstByItemIdOrderByIdAsc(Long itemId);

    List<ItemMedia> findAllByItemIdOrderByIdAsc(Long itemId);

    void deleteAllByItemIdAndIdIn(Long itemId, List<Long> ids);

    void deleteAllByItemId(Long itemId);

    @Query("SELECT im FROM ItemMedia im WHERE im.item.id = :itemId")
    List<ItemMedia> findAllByItemId(@Param("itemId") Long itemId);

    int countByItemId(long itemId);

    int countByItemIdAndIdIn(Long itemId, List<Long> ids);

    List<ItemMedia> findAllByItemIdAndIdIn(Long itemId, List<Long> mediaIds);

    @Query("SELECT m.item.id, m.mediaUrl " +
            "FROM ItemMedia m " +
            "WHERE m.item.id IN :itemIds " +
            "ORDER BY m.id ASC")
    List<Object[]> findByItemIdIn(@Param("itemIds") List<Long> itemIds);
}
