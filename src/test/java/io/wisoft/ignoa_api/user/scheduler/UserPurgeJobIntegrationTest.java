package io.wisoft.ignoa_api.user.scheduler;

import io.wisoft.ignoa_api.auction.scheduler.AuctionCloseScheduler;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import io.wisoft.ignoa_api.support.IntegrationTestSupport;
import io.wisoft.ignoa_api.user.entity.ProfileImageSource;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserPurgeJobIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuctionCloseScheduler auctionCloseScheduler;

    @MockitoBean
    StorageService storageService;

    @Autowired
    UserPurgeJob userPurgeJob;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void 탈퇴_후_30일이_지난_미파기_회원은_배치를_놓친_날짜와_관계없이_파기된다() {
        // Given: 31일 전, 40일 전(배치를 놓친 날), 29일 전(유예 중) 탈퇴 회원
        User expired = saveWithdrawnUser("expired@test.com", "expired", "profiles/expired.jpg", 31);
        User missed = saveWithdrawnUser("missed@test.com", "missed", "profiles/missed.jpg", 40);
        User grace = saveWithdrawnUser("grace@test.com", "grace", "profiles/grace.jpg", 29);

        // When
        userPurgeJob.execute();

        // Then
        assertPurged(expired);
        assertPurged(missed);
        assertNotPurged(grace);
        verify(storageService).delete("profiles/expired.jpg");
        verify(storageService).delete("profiles/missed.jpg");
        verify(storageService, never()).delete("profiles/grace.jpg");
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void S3_삭제에_실패하면_익명화하지_않고_다음_실행에서_다시_파기한다() {
        // Given
        User user = saveWithdrawnUser("retry@test.com", "retry", "profiles/retry.jpg", 31);
        doThrow(new RuntimeException("S3 장애")).when(storageService).delete(anyString());

        // When: 첫 실행은 S3 삭제 실패
        userPurgeJob.execute();

        // Then
        assertNotPurged(user);

        // When: 다음 실행에서 S3 삭제 성공
        doNothing().when(storageService).delete(anyString());
        userPurgeJob.execute();

        // Then
        assertPurged(user);
    }

    @Test
    void 이미_파기된_회원은_다시_파기_대상이_되지_않는다() {
        // Given
        User user = saveWithdrawnUser("purged@test.com", "purged", "profiles/purged.jpg", 31);
        userPurgeJob.execute();
        LocalDateTime firstPurgedAt = userRepository.findById(user.getId()).orElseThrow().getPurgedAt();

        // When
        userPurgeJob.execute();

        // Then: S3 삭제는 첫 실행의 1번뿐이고 파기 시각도 그대로
        verify(storageService).delete("profiles/purged.jpg");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPurgedAt()).isEqualTo(firstPurgedAt);
    }

    private User saveWithdrawnUser(String email, String nickname, String objectKey, int daysAgo) {
        User user = newUser(email, nickname);
        user.updateProfileImage(objectKey, ProfileImageSource.MANAGED);
        user.withdraw();
        User saved = userRepository.save(user);

        jdbcTemplate.update(
                "UPDATE users SET deleted_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(daysAgo),
                saved.getId()
        );

        return saved;
    }

    private void assertPurged(User user) {
        User found = userRepository.findById(user.getId()).orElseThrow();

        assertThat(found.getPurgedAt()).isNotNull();
        assertThat(found.getEmail()).isNull();
        assertThat(found.getNickname()).isEqualTo("탈퇴한 사용자_" + user.getId());
        assertThat(found.getProfileImageReference()).isNull();
    }

    private void assertNotPurged(User user) {
        User found = userRepository.findById(user.getId()).orElseThrow();

        assertThat(found.getPurgedAt()).isNull();
        assertThat(found.getEmail()).isEqualTo(user.getEmail());
        assertThat(found.getProfileImageReference()).isEqualTo(user.getProfileImageReference());
    }
}
