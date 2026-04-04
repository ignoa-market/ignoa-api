package io.wisoft.ignoa_api.wish.controller;

import io.wisoft.ignoa_api.global.dto.ApiResponse;
import io.wisoft.ignoa_api.global.dto.SliceResponse;
import io.wisoft.ignoa_api.wish.dto.request.WishListRequest;
import io.wisoft.ignoa_api.wish.dto.response.WishSummary;
import io.wisoft.ignoa_api.wish.service.WishService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishes")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;

    @PostMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Void>> addWish(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        wishService.addWish(itemId, userId);
        ApiResponse<Void> response = ApiResponse.of(null, "찜 등록에 성공하였습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeWish(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        wishService.removeWish(itemId, userId);
        ApiResponse<Void> response = ApiResponse.of(null, "찜 취소에 성공하였습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<WishSummary>>> getWishes(
            @Valid @ModelAttribute WishListRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        SliceResponse<WishSummary> data = wishService.getWishes(userId, request);
        ApiResponse<SliceResponse<WishSummary>> response = ApiResponse.of(data, "찜 목록을 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
