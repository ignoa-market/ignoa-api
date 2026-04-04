package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.dto.response.ItemMediaInfo;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemMediaService {

    private final ItemMediaRepository itemMediaRepository;
    private final StorageService storageService;

    public String getFirstMediaUrl(Long itemId) {
        return itemMediaRepository
                .findFirstByItemIdOrderByIdAsc(itemId)
                .map(ItemMedia::getMediaUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_MEDIA_NOT_FOUND));
    }

    public List<ItemMediaInfo> getMediaInfoByItemId(Long itemId) {
        return itemMediaRepository.findMediaInfoByItemId(itemId);
    }

    public void validateMinimumMediaCount(Long itemId, List<Long> deleteIds, List<MultipartFile> uploadFiles) {
        int currentCount = itemMediaRepository.countByItemId(itemId);
        int toDeleteCount = itemMediaRepository.countByItemIdAndIdIn(itemId, deleteIds);
        int addCount = uploadFiles == null ? 0 : (int) uploadFiles.stream().filter(file -> !file.isEmpty()).count();

        if (currentCount - toDeleteCount + addCount < 1) {
            throw new BusinessException(ErrorCode.ITEM_MEDIA_REQUIRED);
        }
    }

    @Transactional
    public void saveMedia(Item item, List<MultipartFile> files) {
        List<ItemMedia> itemMediaList = files.stream()
                .filter(file -> !file.isEmpty())
                .map(file -> {
                    String mediaUrl = storageService.upload(file);
                    return ItemMedia.from(item, mediaUrl, ItemMediaType.from(file.getOriginalFilename()));
                })
                .toList();

        if (!itemMediaList.isEmpty()) {
            itemMediaRepository.saveAll(itemMediaList);
        }
    }

    @Transactional
    public void deleteMediaByIds(Long itemId, List<Long> deleteIds) {
        itemMediaRepository.findAllById(deleteIds)
                .forEach(itemMedia -> storageService.delete(itemMedia.getMediaUrl()));

        itemMediaRepository.deleteAllByItemIdAndIdIn(itemId, deleteIds);
    }

    @Transactional
    public void deleteAllByItemId(Long itemId) {
        itemMediaRepository.findAllByItemId(itemId)
                .forEach(itemMedia -> storageService.delete(itemMedia.getMediaUrl()));
        itemMediaRepository.deleteAllByItemId(itemId);
    }
}
