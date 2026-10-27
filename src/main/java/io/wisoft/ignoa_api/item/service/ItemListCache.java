package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.global.config.CacheConfig;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewCursorRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.enums.ItemViewType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemListCache {

    private final ItemQueryService itemQueryService;

    @Cacheable(cacheNames = CacheConfig.ITEM_LIST, key = "#view + ':' + #size", sync = true)
    public CursorResponse<ItemPreview> getFirstPage(ItemViewType view, Integer size) {
        return itemQueryService.getItems(
                new ItemPreviewCursorRequest(view, null, size), null
        );
    }
}
