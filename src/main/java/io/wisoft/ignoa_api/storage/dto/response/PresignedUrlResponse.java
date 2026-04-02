package io.wisoft.ignoa_api.storage.dto.response;

public record PresignedUrlResponse(
        String presignedUrl,
        String objectUrl
) {
}
