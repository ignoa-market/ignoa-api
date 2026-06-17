package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemIdResponse;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ItemFacade {

    private final StorageService storageService;
    private final ItemCommandService itemCommandService;
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
            return itemCommandService.updateItem(itemId, userId, request, uploadedMedias);
        } catch (RuntimeException e) {
            compensate(itemId.toString(), uploadedMedias);
            throw e;
        }
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
        uploadedMedias.forEach(uploadedMedia -> outboxAppender.saveForCompensation(
                aggregateId, "ITEM", uploadedMedia.mediaUrl(), OutboxEventType.DELETE_ITEM_IMAGE));
    }
}
