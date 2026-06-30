package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemIdResponse;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import io.wisoft.ignoa_api.item.support.ItemLockKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemFacade {

    private final StorageService storageService;
    private final ItemCommandService itemCommandService;
    private final RedissonDistributedLock distributedLock;
    private final OutboxAppender outboxAppender;

    public ItemIdResponse createItem(Long sellerId, ItemCreateRequest request, List<MultipartFile> files) {
        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);
            return itemCommandService.createItem(sellerId, request, uploadedMedias);
        } catch (RuntimeException e) {
            compensate(sellerId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files) {
        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);
            return distributedLock.executeWithLock(
                    ItemLockKey.of(itemId),
                    () -> itemCommandService.updateItem(itemId, userId, request, uploadedMedias)
            );
        } catch (RuntimeException e) {
            compensate(itemId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        return distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                () -> itemCommandService.deleteItem(itemId, userId)
        );
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId) {
        return distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                () -> itemCommandService.buyNowItem(itemId, buyerId)
        );
    }

    private void uploadFiles(List<MultipartFile> files, List<UploadedMedia> uploadedMedias) {
        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        for (MultipartFile file : files) {
            String mediaUrl = storageService.upload(file);
            uploadedMedias.add(new UploadedMedia(mediaUrl, ItemMediaType.from(file.getContentType())));
        }
    }

    private void compensate(String aggregateId, List<UploadedMedia> uploadedMedias) {
        try {
            uploadedMedias.forEach(uploadedMedia -> outboxAppender.saveForCompensation(
                    aggregateId,
                    "ITEM",
                    uploadedMedia.mediaUrl(),
                    OutboxEventType.DELETE_ITEM_IMAGE));
        } catch (RuntimeException compensationError) {
            log.error("보상 Outbox 적재 실패 - 고아 파일 수동 정리 필요 - aggregateId={}, mediaUrls={}",
                    aggregateId,
                    uploadedMedias.stream().map(UploadedMedia::mediaUrl).toList(),
                    compensationError);
        }
    }
}
