package io.wisoft.ignoa_api.product.controller;

import io.wisoft.ignoa_api.global.dto.SliceResponse;
import io.wisoft.ignoa_api.product.dto.request.ProductCreateRequest;
import io.wisoft.ignoa_api.product.dto.request.ProductListRequest;
import io.wisoft.ignoa_api.product.dto.request.ProductUpdateRequest;
import io.wisoft.ignoa_api.product.dto.response.ProductResponse;
import io.wisoft.ignoa_api.product.dto.response.ProductDetailResponse;
import io.wisoft.ignoa_api.product.dto.response.ProductSummary;
import io.wisoft.ignoa_api.product.service.ProductService;
import io.wisoft.ignoa_api.global.dto.ApiResponse;
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
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestPart ProductCreateRequest request,
            @RequestPart @NotEmpty(message = "상품 이미지는 최소 1개 이상이어야 합니다.") List<MultipartFile> files,
            @AuthenticationPrincipal Long sellerId
    ) {
        ProductResponse data = productService.createProduct(sellerId, request, files);
        ApiResponse<ProductResponse> response = ApiResponse.of(data, "상품이 등록되었습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<ProductSummary>>> getProducts(
            @Valid @ModelAttribute ProductListRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        SliceResponse<ProductSummary> data = productService.getProducts(userId, request);
        ApiResponse<SliceResponse<ProductSummary>> response = ApiResponse.of(data, "상품 리스트를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long userId
    ) {
        ProductDetailResponse data = productService.getProductDetail(productId, userId);
        ApiResponse<ProductDetailResponse> response = ApiResponse.of(data, "상품 상세 정보를 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @PatchMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestPart ProductUpdateRequest request,
            @RequestPart(required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Long userId
    ) {
        ProductDetailResponse data = productService.updateProduct(productId, userId, request, files);
        ApiResponse<ProductDetailResponse> response = ApiResponse.of(data, "상품 정보를 수정했습니다.");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> deleteProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal Long userId
    ) {
        ProductResponse data = productService.deleteProduct(productId, userId);
        ApiResponse<ProductResponse> response = ApiResponse.of(data, "상품을 삭제했습니다.");
        return ResponseEntity.ok(response);
    }
}
