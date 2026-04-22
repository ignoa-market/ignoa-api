package io.wisoft.ignoa_api.user.repository;

import io.wisoft.ignoa_api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);

    List<User> findAllByDeletedAtBefore(LocalDateTime deletedAtBefore);

    Optional<User> findByProviderAndOauthId(String provider, String oauthId);
}
