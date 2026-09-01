package io.wisoft.ignoa_api.global.infra.storage;

public record StorageUploadResult(
        String objectKey,
        String contentType
) {
}
