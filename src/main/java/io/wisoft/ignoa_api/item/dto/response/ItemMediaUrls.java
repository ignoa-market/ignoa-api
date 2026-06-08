package io.wisoft.ignoa_api.item.dto.response;

import io.wisoft.ignoa_api.item.entity.ItemMedia;

public record ItemMediaUrls(
        Long itemMediaId,
        String url
) {
    public static ItemMediaUrls of(ItemMedia itemMedia) {
        return new ItemMediaUrls(itemMedia.getId(), itemMedia.getMediaUrl());
    }
}
