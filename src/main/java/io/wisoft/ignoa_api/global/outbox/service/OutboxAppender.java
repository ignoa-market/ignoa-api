package io.wisoft.ignoa_api.global.outbox.service;

import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxRepository outboxRepository;

    public void save(String aggregateId, String aggregateType, String imageUrl, OutboxEventType eventType) {
        append(aggregateId, aggregateType, imageUrl, eventType);
        log.info("Outbox 적재 완료 - aggregateId: {}, imageUrl: {}, eventType: {}", aggregateId, imageUrl, eventType);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveForCompensation(String aggregateId, String aggregateType, String imageUrl, OutboxEventType eventType) {
        append(aggregateId, aggregateType, imageUrl, eventType);
        log.warn("보상 Outbox 적재 완료 - aggregateId: {}, imageUrl: {}, eventType: {}", aggregateId, imageUrl, eventType);
    }

    private void append(String aggregateId, String aggregateType, String imageUrl, OutboxEventType eventType) {
        Outbox outbox = Outbox.create(aggregateId, aggregateType, eventType, imageUrl);
        outboxRepository.save(outbox);
    }
}
