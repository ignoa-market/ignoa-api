package io.wisoft.ignoa_api.item.entity.enums;

public enum ItemViewType {
    ALL,
    POPULAR,
    ENDING_SOON,
    LATEST,
    MY_ITEMS,
    MY_BIDS;

    public boolean requiresAuth() {
        return this == MY_ITEMS || this == MY_BIDS;
    }
}
