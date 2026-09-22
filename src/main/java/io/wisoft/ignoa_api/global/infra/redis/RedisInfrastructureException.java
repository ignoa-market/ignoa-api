package io.wisoft.ignoa_api.global.infra.redis;

public class RedisInfrastructureException extends RuntimeException {

    public RedisInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
