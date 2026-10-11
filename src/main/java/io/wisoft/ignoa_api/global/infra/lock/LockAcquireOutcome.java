package io.wisoft.ignoa_api.global.infra.lock;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LockAcquireOutcome {

    ACQUIRED("acquired"),
    TIMEOUT("timeout"),
    INTERRUPTED("interrupted"),
    INFRA_ERROR("infra_error"),
    ERROR("error");

    private final String metricTag;
}