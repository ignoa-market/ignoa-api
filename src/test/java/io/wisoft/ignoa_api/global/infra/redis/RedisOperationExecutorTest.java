package io.wisoft.ignoa_api.global.infra.redis;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisOperationExecutorTest {

    @Test
    void Redis_명령_시간_초과를_Redis_인프라_예외로_변환한다() {
        RedisOperationExecutor executor = new RedisOperationExecutor();
        QueryTimeoutException cause =
                new QueryTimeoutException("Redis 명령 시간 초과");

        Supplier<String> operation = () -> {
            throw cause;
        };

        assertThatThrownBy(() -> executor.execute(operation))
                .isInstanceOf(RedisInfrastructureException.class)
                .hasCause(cause);
    }

    @Test
    void 반환값이_없는_Redis_명령_시간_초과를_Redis_인프라_예외로_변환한다() {
        RedisOperationExecutor executor = new RedisOperationExecutor();
        QueryTimeoutException cause =
                new QueryTimeoutException("Redis 명령 시간 초과");

        Runnable operation = () -> {
            throw cause;
        };

        assertThatThrownBy(() -> executor.run(operation))
                .isInstanceOf(RedisInfrastructureException.class)
                .hasCause(cause);
    }
}
