package io.wisoft.ignoa_api.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ConstantDelay;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    private final String host;
    private final int port;
    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final int retryAttempts;
    private final Duration retryDelay;

    public RedissonConfig(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${ignoa.redis.redisson.connect-timeout}") Duration connectTimeout,
            @Value("${ignoa.redis.redisson.response-timeout}") Duration responseTimeout,
            @Value("${ignoa.redis.redisson.retry-attempts}") int retryAttempts,
            @Value("${ignoa.redis.redisson.retry-delay}") Duration retryDelay
    ) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
        this.responseTimeout = responseTimeout;
        this.retryAttempts = retryAttempts;
        this.retryDelay = retryDelay;
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        configure(config);

        return Redisson.create(config);
    }

    SingleServerConfig configure(Config config) {
        return config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout(toMillis(connectTimeout))
                .setTimeout(toMillis(responseTimeout))
                .setRetryAttempts(retryAttempts)
                .setRetryDelay(new ConstantDelay(retryDelay));
    }

    private int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
