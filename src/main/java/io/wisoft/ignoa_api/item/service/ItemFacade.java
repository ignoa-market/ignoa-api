package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.storage.ObjectKeyPrefix;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.infra.storage.StorageUploadResult;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewCursorRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemIdResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.service.dto.UploadedMedia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemFacade {

    private final StorageService storageService;
    private final ItemCommandService itemCommandService;
    private final ItemQueryService itemQueryService;
    private final ItemPreviewAssembler itemPreviewAssembler;
    private final ItemListCache itemListCache;
    private final OutboxAppender outboxAppender;

    public CursorResponse<ItemPreview> getItems(ItemPreviewCursorRequest request, Long userId) {
        if (request.cursor() != null) {
            return itemQueryService.getItems(request, userId);
        }

        CursorResponse<ItemPreview> firstPage = itemListCache.getFirstPage(request.view(), request.size());
        List<ItemPreview> itemPreviews = itemPreviewAssembler.applyWished(userId, firstPage.content());

        return CursorResponse.of(itemPreviews, firstPage.nextCursor(), firstPage.hasNext());
    }

    public ItemIdResponse createItem(Long sellerId, ItemCreateRequest request, List<MultipartFile> files) {
        validateMediaComposition(files);

        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);
            return itemCommandService.createItem(sellerId, request, uploadedMedias);

        } catch (RuntimeException e) {
            compensateAll(sellerId.toString(), "sellerId", uploadedMedias);
            throw e;
        }
    }

    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files) {
        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);
            return itemCommandService.updateItem(itemId, userId, request, uploadedMedias);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("상품 수정 충돌: itemId={}, reason=낙관적 락 충돌", itemId);
            compensateAll(itemId.toString(), "itemId", uploadedMedias);
            throw new BusinessException(ErrorCode.ITEM_CONFLICT);

        } catch (RuntimeException e) {
            compensateAll(itemId.toString(), "itemId", uploadedMedias);
            throw e;
        }
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        return itemCommandService.deleteItem(itemId, userId);
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId, ItemBuyNowRequest request) {
        return itemCommandService.buyNowItem(itemId, buyerId, request);
    }

    // 업로드 전 미디어 구성 검증 (동영상 최대 1개, 이미지 최소 1장)
    private void validateMediaComposition(List<MultipartFile> files) {
        List<ItemMediaType> mediaTypes = files.stream()
                .map(file -> ItemMediaType.from(storageService.detectContentType(file)))
                .toList();

        if (Collections.frequency(mediaTypes, ItemMediaType.VIDEO) > 1) {
            throw new BusinessException(ErrorCode.ITEM_VIDEO_LIMIT_EXCEEDED);
        }

        if (!mediaTypes.contains(ItemMediaType.IMAGE)) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_REQUIRED);
        }
    }

    // 스토리지 파일 업로드 및 결과 수집
    private void uploadFiles(List<MultipartFile> files, List<UploadedMedia> uploadedMedias) {
        // 미디어 변경 없는 수정 요청은 업로드 생략
        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        for (MultipartFile file : files) {
            StorageUploadResult uploadResult = storageService.upload(file, ObjectKeyPrefix.ITEMS);

            uploadedMedias.add(
                    new UploadedMedia(
                            uploadResult.objectKey(),
                            ItemMediaType.from(uploadResult.contentType())
                    )
            );
        }
    }

    private void compensateAll(String aggregateId, String identifierName, List<UploadedMedia> uploadedMedias) {
        for (UploadedMedia uploadedMedia : uploadedMedias) {
            compensate(aggregateId, identifierName, uploadedMedia);
        }
    }

    // DB 작업 실패 시 업로드 파일 삭제를 위한 보상 Outbox 등록
    private void compensate(String aggregateId, String identifierName, UploadedMedia uploadedMedia) {
        try {
            outboxAppender.saveForCompensation(
                    aggregateId,
                    "ITEM",
                    uploadedMedia.objectKey(),
                    OutboxEventType.DELETE_ITEM_MEDIA);

        } catch (RuntimeException compensationError) {
            log.error(
                    "보상 Outbox 적재 실패: aggregateType=ITEM, {}={}, objectKey={}, action=업로드 파일 수동 확인",
                    identifierName,
                    aggregateId,
                    uploadedMedia.objectKey(),
                    compensationError
            );
        }
    }
}
