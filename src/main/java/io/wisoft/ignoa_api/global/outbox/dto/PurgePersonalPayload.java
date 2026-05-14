package io.wisoft.ignoa_api.global.outbox.dto;

public record PurgePersonalPayload(
        Long userId,
        String imageUrl
) {
}
