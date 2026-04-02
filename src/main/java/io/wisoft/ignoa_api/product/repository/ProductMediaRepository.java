package io.wisoft.ignoa_api.product.repository;

import io.wisoft.ignoa_api.product.dto.response.ProductMediaInfo;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {

    Optional<ProductMedia> findFirstByProductIdOrderByIdAsc(Long productId);

    @Query("SELECT new io.wisoft.ignoa_api.product.dto.response.ProductMediaInfo(pi.id, pi.mediaUrl) " +
            "FROM ProductMedia pi " +
            "WHERE pi.product.id = :productId " +
            "ORDER BY pi.id ASC")
    List<ProductMediaInfo> findMediaInfoByProductId(@Param("productId") Long productId);

    void deleteAllByProductIdAndIdIn(Long productId, List<Long> ids);

    void deleteAllByProductId(Long productId);

    @Query("SELECT pm FROM ProductMedia pm WHERE pm.product.id = :productId")
    List<ProductMedia> findAllByProductId(@Param("productId") Long productId);

    int countByProductId(long productId);

    int countByProductIdAndIdIn(Long productId, List<Long> ids);
}
