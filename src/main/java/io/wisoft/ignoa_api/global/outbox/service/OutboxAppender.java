package io.wisoft.ignoa_api.global.outbox.service;

import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxRepository outboxRepository;

    public void save(String aggregateId, String aggregateType, String imageUrl, OutboxEventType eventType) {
            Outbox outbox = Outbox.create(aggregateId, aggregateType, eventType, imageUrl);
            outboxRepository.save(outbox);
            log.info("Outbox 저장 완료 - aggregateId: {}, eventType: {}", aggregateId, eventType);
    }
}
