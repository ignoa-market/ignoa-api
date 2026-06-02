package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.response.ItemMediaResponse;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemMediaService {

    private final StorageService storageService;
    private final OutboxAppender outboxAppender;
    private final ItemMediaRepository itemMediaRepository;

    public String getFirstMediaUrl(Long itemId) {
        return itemMediaRepository
                .findFirstByItemIdOrderByIdAsc(itemId)
                .map(ItemMedia::getMediaUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_MEDIA_NOT_FOUND));
    }

    public List<ItemMediaResponse> getMediaUrlByItemId(Long itemId) {
        return itemMediaRepository.findAllByItemIdOrderByIdAsc(itemId).stream()
                .map(itemMedia ->
                        new ItemMediaResponse(itemMedia.getId(), itemMedia.getMediaUrl()))
                .toList();
    }

    public void validateMinimumMediaCount(Long itemId, List<Long> mediaIds, List<MultipartFile> files) {
        int currentCount = itemMediaRepository.countByItemId(itemId);
        int toDeleteCount = itemMediaRepository.countByItemIdAndIdIn(itemId, mediaIds);
        int addCount = files == null ? 0 : (int) files.stream().filter(file -> !file.isEmpty()).count();

        if (currentCount - toDeleteCount + addCount < 1) {
            throw new BusinessException(ErrorCode.ITEM_MEDIA_REQUIRED);
        }
    }

    @Transactional
    public void save(Item item, List<MultipartFile> files) {
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
    public void deleteByIds(Long itemId, List<Long> mediaIds) {
        itemMediaRepository.findAllByItemIdAndIdIn(itemId, mediaIds)
                .forEach(itemMedia -> outboxAppender.save(
                        itemId.toString(), "ITEM", itemMedia.getMediaUrl(), OutboxEventType.DELETE_ITEM_IMAGE
                ));

        itemMediaRepository.deleteAllByItemIdAndIdIn(itemId, mediaIds);
    }

    @Transactional
    public void deleteAllByItemId(Long itemId) {
        itemMediaRepository.findAllByItemId(itemId)
                .forEach(itemMedia -> outboxAppender.save(
                        itemId.toString(), "ITEM", itemMedia.getMediaUrl(), OutboxEventType.DELETE_ITEM_IMAGE
                ));

        itemMediaRepository.deleteAllByItemId(itemId);
    }
}
