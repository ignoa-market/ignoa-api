package io.wisoft.ignoa_api.global.outbox.repository;

import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
}
