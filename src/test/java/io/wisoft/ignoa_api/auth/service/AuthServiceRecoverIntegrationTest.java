package io.wisoft.ignoa_api.auth.service;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.auth.dto.request.LoginRequest;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceRecoverIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password";

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void 유예_기간_안의_탈퇴_회원은_복구된다() {
        // Given
        User user = saveWithdrawnUser("recover@test.com", "recover", User.WITHDRAWAL_GRACE_DAYS - 1);

        // When
        authService.recover(new LoginRequest("recover@test.com", PASSWORD));

        // Then
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void 유예_기간이_지난_탈퇴_회원은_파기_전이어도_복구할_수_없다() {
        // Given: 파기 배치가 아직 처리하지 않은 유예 기간 경과 회원
        User user = saveWithdrawnUser("expired@test.com", "expired", User.WITHDRAWAL_GRACE_DAYS + 1);

        // When & Then
        assertThatThrownBy(() -> authService.recover(new LoginRequest("expired@test.com", PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_RECOVERABLE);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
    }

    private User saveWithdrawnUser(String email, String nickname, int daysAgo) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), nickname, "address");
        user.withdraw();
        User saved = userRepository.save(user);

        jdbcTemplate.update(
                "UPDATE users SET deleted_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(daysAgo),
                saved.getId()
        );

        return saved;
    }
}
