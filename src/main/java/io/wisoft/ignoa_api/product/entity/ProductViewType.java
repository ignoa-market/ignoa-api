package io.wisoft.ignoa_api.product.entity;

public enum ProductViewType {
    ALL,
    POPULAR,
    ENDING_SOON,
    LATEST,
    MY_PRODUCTS,
    MY_BIDS;

    public boolean requiresAuth() {
        return this == MY_PRODUCTS || this == MY_BIDS;
    }
}
