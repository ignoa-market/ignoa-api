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

    List<User> findAllByDeletedAtBefore(LocalDateTime deletedAtBefore);

    Optional<User> findByProviderAndOauthId(String provider, String oauthId);

    @Query("""
                    SELECT u 
                    FROM User u
                    WHERE u.deletedAt >= :startDateTime
                      AND u.deletedAt < :endDateTime
                      AND u.id > :lastId
                    ORDER BY u.id ASC    
            """)
    List<User> findPurgeTargets(@Param("startDateTime") LocalDateTime startDateTime,
                                @Param("endDateTime") LocalDateTime endDateTime,
                                @Param("lastId") Long lastId,
                                Pageable pageable);
}

