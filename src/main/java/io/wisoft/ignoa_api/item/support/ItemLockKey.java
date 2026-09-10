package io.wisoft.ignoa_api.item.support;

public class ItemLockKey {

    private static final String PREFIX = "item:lock:";

    private ItemLockKey() {}

    public static String of(Long itemId) {
        return PREFIX + itemId;
    }
}
