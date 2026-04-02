package io.wisoft.ignoa_api.product.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.product.dto.response.ProductMediaInfo;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.product.entity.ProductMedia;
import io.wisoft.ignoa_api.product.entity.ProductMediaType;
import io.wisoft.ignoa_api.product.repository.ProductMediaRepository;
import io.wisoft.ignoa_api.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductMediaService {

    private final ProductMediaRepository productMediaRepository;
    private final StorageService storageService;

    public String getFirstMediaUrl(Long productId) {
        return productMediaRepository
                .findFirstByProductIdOrderByIdAsc(productId)
                .map(ProductMedia::getMediaUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_MEDIA_NOT_FOUND));
    }

    public List<ProductMediaInfo> getMediaInfoByProductId(Long productId) {
        return productMediaRepository.findMediaInfoByProductId(productId);
    }

    public void validateMinimumMediaCount(Long productId, List<Long> deleteIds, List<MultipartFile> uploadFiles) {
        int currentCount = productMediaRepository.countByProductId(productId);
        int toDeleteCount = productMediaRepository.countByProductIdAndIdIn(productId, deleteIds);
        int addCount = uploadFiles == null ? 0 : (int) uploadFiles.stream().filter(file -> !file.isEmpty()).count();

        if (currentCount - toDeleteCount + addCount < 1) {
            throw new BusinessException(ErrorCode.PRODUCT_MEDIA_REQUIRED);
        }
    }

    @Transactional
    public void saveMedia(Product product, List<MultipartFile> files) {
        List<ProductMedia> productMediaList = files.stream()
                .filter(file -> !file.isEmpty())
                .map(file -> {
                    String mediaUrl = storageService.upload(file);
                    return ProductMedia.from(product, mediaUrl, ProductMediaType.from(file.getOriginalFilename()));
                })
                .toList();

        if (!productMediaList.isEmpty()) {
            productMediaRepository.saveAll(productMediaList);
        }
    }

    @Transactional
    public void deleteMediaByIds(Long productId, List<Long> deleteIds) {
        productMediaRepository.findAllById(deleteIds)
                .forEach(productMedia -> storageService.delete(productMedia.getMediaUrl()));

        productMediaRepository.deleteAllByProductIdAndIdIn(productId, deleteIds);
    }

    @Transactional
    public void deleteAllByProductId(Long productId) {
        productMediaRepository.findAllByProductId(productId)
                .forEach(productMedia -> storageService.delete(productMedia.getMediaUrl()));
        productMediaRepository.deleteAllByProductId(productId);
    }
}
