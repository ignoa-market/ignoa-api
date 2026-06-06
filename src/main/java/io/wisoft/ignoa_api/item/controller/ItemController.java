package io.wisoft.ignoa_api.item.controller;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.BuyNowResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemIdResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.service.ItemCommandService;
import io.wisoft.ignoa_api.item.service.ItemQueryService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemQueryService itemQueryService;
    private final ItemCommandService itemCommandService;

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<ItemPreview>>> getItems(
            @Valid @ModelAttribute ItemPreviewRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        SliceResponse<ItemPreview> data = itemQueryService.getItems(request, userId);
        ApiResponse<SliceResponse<ItemPreview>> response = ApiResponse.of(data, "상품 리스트를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemDetail>> getItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        ItemDetail data = itemQueryService.getItem(itemId, userId);
        ApiResponse<ItemDetail> response = ApiResponse.of(data, "상품 상세 정보를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemIdResponse>> createItem(
            @Valid @RequestPart ItemCreateRequest request,
            @RequestPart @NotEmpty(message = "상품 이미지는 최소 1개 이상이어야 합니다.") List<MultipartFile> files,
            @AuthenticationPrincipal Long sellerId
    ) {
        ItemIdResponse data = itemCommandService.createItem(sellerId, request, files);
        ApiResponse<ItemIdResponse> response = ApiResponse.of(data, "상품이 등록되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping(value = "/{itemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemDetail>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestPart ItemUpdateRequest request,
            @RequestPart(required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Long userId
    ) {
        ItemDetail data = itemCommandService.updateItem(itemId, userId, request, files);
        ApiResponse<ItemDetail> response = ApiResponse.of(data, "상품 정보를 수정했습니다.");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemIdResponse>> deleteItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        ItemIdResponse data = itemCommandService.deleteItem(itemId, userId);
        ApiResponse<ItemIdResponse> response = ApiResponse.of(data, "상품을 삭제했습니다.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{itemId}/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyNowItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long buyerId
    ) {
        BuyNowResponse data = itemCommandService.buyNowItem(itemId, buyerId);
        ApiResponse<BuyNowResponse> response = ApiResponse.of(data, "상품을 즉시 구매하였습니다.");
        return ResponseEntity.ok(response);
    }
}
