package io.wisoft.ignoa_api.item.service.dto;

import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;

public record UploadedMedia(
        String mediaUrl,
        ItemMediaType mediaType
) {
}
