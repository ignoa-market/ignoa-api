package io.wisoft.ignoa_api.item.dto.response;


import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;

public record ItemMediaUrls(
        Long itemMediaId,
        String url,
        ItemMediaType itemMediaType
) {
}
