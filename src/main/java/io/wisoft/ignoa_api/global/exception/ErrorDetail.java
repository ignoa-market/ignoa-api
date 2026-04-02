package io.wisoft.ignoa_api.global.exception;

public record ErrorDetail(
        String field,
        String message
) {
}
