package io.wisoft.ignoa_api.user.repository;

import io.wisoft.ignoa_api.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderAndOauthId(String provider, String oauthId);

    // 탈퇴 후 기준 시점이 지났고 아직 파기되지 않은 회원을 마지막 처리 ID 이후부터 순서대로 조회
    @Query("""
            SELECT u
            FROM User u
            WHERE u.deletedAt < :cutoff
                AND u.id > :lastId
                AND u.purgedAt IS NULL
            ORDER BY u.id ASC
            """)
    List<User> findPurgeTargets(@Param("cutoff") LocalDateTime cutoff, @Param("lastId") Long lastId,
                                Pageable pageable);

    boolean existsByEmailAndProvider(String email, String local);
}