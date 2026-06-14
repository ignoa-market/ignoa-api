package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.infra.storage.StorageService;
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

import java.util.List;

@Component
@RequiredArgsConstructor
public class ItemFacade {

    private final StorageService storageService;
    private final ItemCommandService itemCommandService;

    public ItemIdResponse createItem(
            Long sellerId, ItemCreateRequest request, List<MultipartFile> files
    ) {
        List<UploadedMedia> uploadedMedias = uploadFiles(files);
        return itemCommandService.createItem(sellerId, request, uploadedMedias);
    }

    public ItemDetail updateItem(
            Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files
    ) {
        List<UploadedMedia> uploadedMedias = CollectionUtils.isEmpty(files)
                ? List.of()
                : uploadFiles(files);

        return itemCommandService.updateItem(itemId, userId, request, uploadedMedias);
    }

    private List<UploadedMedia> uploadFiles(List<MultipartFile> files) {
        return files.stream()
                .filter(file ->!file.isEmpty())
                .map(file -> new UploadedMedia(
                        storageService.upload(file),
                        ItemMediaType.from(file.getContentType())
                )).toList();
    }
}
