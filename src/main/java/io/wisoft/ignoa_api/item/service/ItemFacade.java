package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockOperation;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.global.infra.storage.ObjectKeyPrefix;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.infra.storage.StorageUploadResult;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.dto.request.ItemBuyNowRequest;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemFacade {

    private static final long BUY_NOW_WAIT_MILLIS = 250L;
    private static final long MODIFY_WAIT_MILLIS = 1_000L;

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
            compensateAll(sellerId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files) {
        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);

            return distributedLock.executeWithLockOrFailOpen(
                    ItemLockKey.of(itemId),
                    LockOperation.UPDATE,
                    MODIFY_WAIT_MILLIS,
                    () -> itemCommandService.updateItem(itemId, userId, request, uploadedMedias)
            );
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("상품 수정 충돌: itemId={}, reason=낙관적 락 충돌", itemId);
            compensateAll(itemId.toString(), uploadedMedias);
            throw new BusinessException(ErrorCode.ITEM_CONFLICT);

        } catch (RuntimeException e) {
            compensateAll(itemId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.DELETE,
                MODIFY_WAIT_MILLIS,
                () -> itemCommandService.deleteItem(itemId, userId));
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId, ItemBuyNowRequest request) {
        return distributedLock.executeWithLockOrFailOpen(
                ItemLockKey.of(itemId),
                LockOperation.BUY_NOW,
                BUY_NOW_WAIT_MILLIS,
                () -> itemCommandService.buyNowItem(itemId, buyerId, request));
    }

    // 전달된 파일을 스토리지에 업로드하고 업로드 결과를 수집한다.
    private void uploadFiles(List<MultipartFile> files, List<UploadedMedia> uploadedMedias) {
        // 상품 수정 시 미디어 변경이 없을 수 있으므로 빈 파일 목록은 처리하지 않는다.
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

    private void compensateAll(String aggregateId, List<UploadedMedia> uploadedMedias) {
        for (UploadedMedia uploadedMedia : uploadedMedias) {
            compensate(aggregateId, uploadedMedia);
        }
    }

    // DB 작업 실패 시, S3에 업로드된 미디어의 삭제를 보상 Outbox에 등록
    private void compensate(String aggregateId, UploadedMedia uploadedMedia) {
        try {
            outboxAppender.saveForCompensation(
                    aggregateId,
                    "ITEM",
                    uploadedMedia.objectKey(),
                    OutboxEventType.DELETE_ITEM_IMAGE);

        } catch (RuntimeException compensationError) {
            log.error("보상 Outbox 적재 실패: aggregateType=ITEM, aggregateId={}, objectKey={}, action=고아 파일 수동 정리",
                    aggregateId,
                    uploadedMedia.objectKey(),
                    compensationError);
        }
    }
}
