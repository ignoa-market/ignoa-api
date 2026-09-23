package io.wisoft.ignoa_api.global.infra.redis;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class RedisOperationExecutor {

    public <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();

        } catch (QueryTimeoutException e) {
            throw new RedisInfrastructureException("Redis 명령 시간 초과", e);
        }
    }

    public void run(Runnable operation) {
        try {
            operation.run();

        } catch (QueryTimeoutException e) {
            throw new RedisInfrastructureException("Redis 명령 시간 초과", e);
        }
    }
}
