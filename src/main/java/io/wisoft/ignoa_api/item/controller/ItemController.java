package io.wisoft.ignoa_api.item.controller;

import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemCreateRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemListRequest;
import io.wisoft.ignoa_api.item.dto.request.ItemUpdateRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemResponse;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.service.ItemService;
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

    private final ItemService itemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemResponse>> createItem(
            @Valid @RequestPart ItemCreateRequest request,
            @RequestPart @NotEmpty(message = "상품 이미지는 최소 1개 이상이어야 합니다.") List<MultipartFile> files,
            @AuthenticationPrincipal Long sellerId
    ) {
        ItemResponse data = itemService.createItem(sellerId, request, files);
        ApiResponse<ItemResponse> response = ApiResponse.of(data, "상품이 등록되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<ItemPreview>>> getItems(
            @Valid @ModelAttribute ItemListRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        SliceResponse<ItemPreview> data = itemService.getItems(userId, request);
        ApiResponse<SliceResponse<ItemPreview>> response = ApiResponse.of(data, "상품 리스트를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemDetail>> getItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        ItemDetail data = itemService.getItemDetail(itemId, userId);
        ApiResponse<ItemDetail> response = ApiResponse.of(data, "상품 상세 정보를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/{itemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ItemDetail>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestPart ItemUpdateRequest request,
            @RequestPart(required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Long userId
    ) {
        ItemDetail data = itemService.updateItem(itemId, userId, request, files);
        ApiResponse<ItemDetail> response = ApiResponse.of(data, "상품 정보를 수정했습니다.");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemResponse>> deleteItem(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        ItemResponse data = itemService.deleteItem(itemId, userId);
        ApiResponse<ItemResponse> response = ApiResponse.of(data, "상품을 삭제했습니다.");
        return ResponseEntity.ok(response);
    }
}
