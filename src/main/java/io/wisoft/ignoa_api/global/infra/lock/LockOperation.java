package io.wisoft.ignoa_api.global.infra.lock;

public enum LockOperation {

    BID("bid"),
    BUY_NOW("buy_now"),
    EXTEND("extend"),
    UPDATE("update"),
    DELETE("delete"),
    AUTO_CLOSE("auto_close");

    private final String metricTag;

    LockOperation(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}



