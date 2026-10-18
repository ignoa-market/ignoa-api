package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceRedisIntegrationTest extends IntegrationTestSupport {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String USER_KEY = "rt:user:" + USER_ID;
    private static final String OTHER_USER_KEY = "rt:user:" + OTHER_USER_ID;

    private static final String TOKEN_A = "token-a";
    private static final String TOKEN_B = "token-b";
    private static final String OTHER_TOKEN = "other-token";

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(List.of(
                "rt:" + TOKEN_A, "rt:" + TOKEN_B, "rt:" + OTHER_TOKEN, USER_KEY, OTHER_USER_KEY
        ));
    }

    @Test
    void 저장하면_사용자_목록에_토큰이_추가되고_만료_시간이_설정된다() {
        // When
        refreshTokenService.save(TOKEN_A, USER_ID);

        // Then
        assertThat(redisTemplate.opsForSet().members(USER_KEY)).containsExactly(TOKEN_A);
        assertThat(redisTemplate.getExpire(USER_KEY, TimeUnit.SECONDS)).isPositive();
    }

    @Test
    void 전체_폐기하면_해당_사용자의_RT만_삭제된다() {
        // Given
        refreshTokenService.save(TOKEN_A, USER_ID);
        refreshTokenService.save(TOKEN_B, USER_ID);
        refreshTokenService.save(OTHER_TOKEN, OTHER_USER_ID);

        // When
        refreshTokenService.deleteAllByUserId(USER_ID);

        // Then
        assertThat(refreshTokenService.consume(TOKEN_A)).isNull();
        assertThat(refreshTokenService.consume(TOKEN_B)).isNull();
        assertThat(redisTemplate.hasKey(USER_KEY)).isFalse();

        assertThat(redisTemplate.opsForSet().members(OTHER_USER_KEY)).containsExactly(OTHER_TOKEN);
        assertThat(refreshTokenService.consume(OTHER_TOKEN)).isEqualTo(OTHER_USER_ID);
    }

    @Test
    void 재발급으로_소비한_RT는_사용자_목록에서_제거된다() {
        // Given
        refreshTokenService.save(TOKEN_A, USER_ID);
        refreshTokenService.save(TOKEN_B, USER_ID);

        // When
        Long userId = refreshTokenService.consume(TOKEN_A);

        // Then
        assertThat(userId).isEqualTo(USER_ID);
        assertThat(redisTemplate.opsForSet().members(USER_KEY)).containsExactly(TOKEN_B);
    }

    @Test
    void 로그아웃으로_삭제한_RT는_사용자_목록에서_제거된다() {
        // Given
        refreshTokenService.save(TOKEN_A, USER_ID);
        refreshTokenService.save(TOKEN_B, USER_ID);

        // When
        refreshTokenService.delete(TOKEN_A);

        // Then
        assertThat(refreshTokenService.consume(TOKEN_A)).isNull();
        assertThat(redisTemplate.opsForSet().members(USER_KEY)).containsExactly(TOKEN_B);
    }
}
