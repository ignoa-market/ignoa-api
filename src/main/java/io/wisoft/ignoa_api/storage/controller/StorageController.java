package io.wisoft.ignoa_api.storage.controller;

import io.wisoft.ignoa_api.global.dto.ApiResponse;
import io.wisoft.ignoa_api.storage.dto.request.PresignedUrlRequest;
import io.wisoft.ignoa_api.storage.dto.response.PresignedUrlResponse;
import io.wisoft.ignoa_api.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request
    ) {
        PresignedUrlResponse data = storageService.generatePresignedUrl(request);
        ApiResponse<PresignedUrlResponse> response = ApiResponse.of(data, "Presigned URL이 발급되었습니다.");
        return ResponseEntity.ok(response);
    }
}
