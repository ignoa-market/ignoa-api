package io.wisoft.ignoa_api.item.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewCursorRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.entity.enums.ItemViewType;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
public class ItemListCache {

    public static final String CACHE_NAME = "itemList";

    private final LoadingCache<ListKey, CursorResponse<ItemPreview>> cache;

    public ItemListCache(ItemQueryService itemQueryService, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofSeconds(2))
                .expireAfterWrite(Duration.ofSeconds(10))
                .maximumSize(200)
                .recordStats()
                .build(listkey -> itemQueryService.getItems(
                        new ItemPreviewCursorRequest(listkey.view(), null, listkey.size()), null
                ));

        CaffeineCacheMetrics.monitor(meterRegistry, cache, CACHE_NAME);
    }

    public CursorResponse<ItemPreview> getFirstPage(ItemViewType view, Integer size) {
        return cache.get(new ListKey(view, size));
    }

    private record ListKey(ItemViewType view, Integer size) {
    }
}
