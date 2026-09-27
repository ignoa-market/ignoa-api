package io.wisoft.ignoa_api.global.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonConfigTest {

    @Test
    void 연결_응답_재시도_설정을_Redisson에_적용한다() {
        RedissonConfig redissonConfig = new RedissonConfig(
                "redis",
                6379,
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                1,
                Duration.ofMillis(100)
        );

        SingleServerConfig singleServerConfig = redissonConfig.configure(new Config());

        assertThat(singleServerConfig.getAddress()).isEqualTo("redis://redis:6379");
        assertThat(singleServerConfig.getConnectTimeout()).isEqualTo(500);
        assertThat(singleServerConfig.getTimeout()).isEqualTo(500);
        assertThat(singleServerConfig.getRetryAttempts()).isEqualTo(1);
        assertThat(singleServerConfig.getRetryDelay().calcDelay(0))
                .isEqualTo(Duration.ofMillis(100));
    }
}
