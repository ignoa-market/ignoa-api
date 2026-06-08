package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.response.ItemMediaUrls;
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
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<Long, String> getFirstMediaUrlMap(List<Long> itemIds) {
        return itemMediaRepository
                .findByItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (String) row[1],
                        (existing, replacement) -> existing
                ));
    }

    public List<ItemMediaUrls> getMediaUrls(Long itemId) {
        return itemMediaRepository
                .findAllByItemIdOrderByIdAsc(itemId).stream()
                .map(ItemMediaUrls::of)
                .toList();
    }

    public void validateMediaCount(
            Long itemId, List<Long> mediaIds, List<MultipartFile> files
    ) {
        int currentCount = itemMediaRepository.countByItemId(itemId);
        int toDeleteCount = itemMediaRepository.countByItemIdAndIdIn(itemId, mediaIds);
        int addCount = files == null ? 0 : files.size();

        if (currentCount - toDeleteCount + addCount < 1) {
            throw new BusinessException(ErrorCode.ITEM_MEDIA_REQUIRED);
        }
    }

    @Transactional
    public void save(Item item, List<MultipartFile> files) {
        files.stream().filter(file -> !file.isEmpty())
                .forEach(file -> {
                    String mediaUrl = storageService.upload(file);
                    ItemMedia itemMedia = ItemMedia.from(item, mediaUrl, ItemMediaType.from(file.getContentType()));
                    itemMediaRepository.save(itemMedia);
                });
    }

    @Transactional
    public void deleteMedias(Long itemId, List<Long> mediaIds) {
        itemMediaRepository.findAllByItemIdAndIdIn(itemId, mediaIds)
                .forEach(itemMedia -> outboxAppender.save(
                        itemId.toString(), "ITEM", itemMedia.getMediaUrl(), OutboxEventType.DELETE_ITEM_IMAGE
                ));

        itemMediaRepository.deleteAllByItemIdAndIdIn(itemId, mediaIds);
    }

    @Transactional
    public void deleteAllMedia(Long itemId) {
        itemMediaRepository.findAllByItemId(itemId)
                .forEach(itemMedia -> outboxAppender.save(
                        itemId.toString(), "ITEM", itemMedia.getMediaUrl(), OutboxEventType.DELETE_ITEM_IMAGE
                ));

        itemMediaRepository.deleteAllByItemId(itemId);
    }
}
