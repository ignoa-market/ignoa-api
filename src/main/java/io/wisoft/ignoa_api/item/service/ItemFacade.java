package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.lock.LockInfrastructureException;
import io.wisoft.ignoa_api.global.infra.lock.RedissonDistributedLock;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
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
            compensate(sellerId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemDetail updateItem(Long itemId, Long userId, ItemUpdateRequest request, List<MultipartFile> files) {
        List<UploadedMedia> uploadedMedias = new ArrayList<>();

        try {
            uploadFiles(files, uploadedMedias);

            try {
                return distributedLock.executeWithLock(
                        ItemLockKey.of(itemId),
                        MODIFY_WAIT_MILLIS,
                        () -> itemCommandService.updateItem(itemId, userId, request, uploadedMedias));
            } catch (LockInfrastructureException e) {
                log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 상품 수정을 진행 itemId={}", itemId, e);
                return itemCommandService.updateItem(itemId, userId, request, uploadedMedias);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("상품 수정 낙관적 락 충돌 itemId={}", itemId, e);
            compensate(itemId.toString(), uploadedMedias);
            throw new BusinessException(ErrorCode.ITEM_CONFLICT);
        } catch (RuntimeException e) {
            compensate(itemId.toString(), uploadedMedias);
            throw e;
        }
    }

    public ItemIdResponse deleteItem(Long itemId, Long userId) {
        return distributedLock.executeWithLock(
                ItemLockKey.of(itemId),
                MODIFY_WAIT_MILLIS,
                () -> itemCommandService.deleteItem(itemId, userId)
        );
    }

    public BuyNowResponse buyNowItem(Long itemId, Long buyerId, ItemBuyNowRequest request) {
        try {
            return distributedLock.executeWithLock(
                    ItemLockKey.of(itemId),
                    BUY_NOW_WAIT_MILLIS,
                    () -> itemCommandService.buyNowItem(itemId, buyerId, request)
            );
        } catch (LockInfrastructureException e) {
            log.warn("Redis 인프라 장애로 fail-open 처리 - 락 없이 즉시구매 진행 itemId={}, buyerId={}", itemId, buyerId, e);
            return itemCommandService.buyNowItem(itemId, buyerId, request);
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
