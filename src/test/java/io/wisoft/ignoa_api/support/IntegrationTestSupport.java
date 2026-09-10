package io.wisoft.ignoa_api.support;


import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.user.entity.User;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0");

    static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
            "redis:7-alpine").withExposedPorts(6379);

    static {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port",
                () -> REDIS_CONTAINER.getMappedPort(6379));
    }

    protected User newUser(String email, String nickname) {
        return new User(email, "password", nickname, "address");
    }

    protected Item newItem(User seller) {
        return newItem(seller, LocalDateTime.now().plusDays(1));
    }

    protected Item newItem(User seller, LocalDateTime endAt) {
        return Item.create(
                seller,
                "테스트 상품",
                "설명",
                "카테고리",
                ItemCondition.GOOD,
                "브랜드",
                1_000L,
                1_000_000L,
                endAt
        );
    }
}
