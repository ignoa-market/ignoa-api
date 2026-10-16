package io.wisoft.ignoa_api.global.infra.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LockOperation {

    BID("bid", 250L),
    EXTEND("extend", 500L),
    UPDATE("update", 1_000L),
    DELETE("delete", 1_000L);

    private final String metricTag;
    private final long waitMillis;
}



