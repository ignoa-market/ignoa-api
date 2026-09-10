package io.wisoft.ignoa_api.global.infra.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ObjectKeyPrefix {

    ITEMS("items"),
    PROFILES("profiles");

    private final String value;
}

