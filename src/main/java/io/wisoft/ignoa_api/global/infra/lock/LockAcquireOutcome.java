package io.wisoft.ignoa_api.global.infra.lock;

public enum LockAcquireOutcome {

    ACQUIRED("acquired"),
    TIMEOUT("timeout"),
    INTERRUPTED("interrupted"),
    INFRA_ERROR("infra_error"),
    ERROR("error");

    private final String metricTag;

    LockAcquireOutcome(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}