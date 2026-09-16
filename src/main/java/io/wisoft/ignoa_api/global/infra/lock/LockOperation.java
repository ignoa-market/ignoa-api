package io.wisoft.ignoa_api.global.infra.lock;

public enum LockOperation {

    BID("bid", 250L),
    BUY_NOW("buy_now", 500L),
    EXTEND("extend", 500L),
    UPDATE("update", 1_000L),
    DELETE("delete", 1_000L),
    AUTO_CLOSE("auto_close", 500L);

    private final String metricTag;

    private final long waitMillis;

    LockOperation(String metricTag, long waitMillis) {
        this.metricTag = metricTag;
        this.waitMillis = waitMillis;
    }

    public String metricTag() {
        return metricTag;
    }

    public long waitMillis() {
        return waitMillis;
    }
}



