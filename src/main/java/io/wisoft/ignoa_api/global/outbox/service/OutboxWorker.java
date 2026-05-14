package io.wisoft.ignoa_api.global.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.dto.PurgePersonalPayload;
import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxStatus;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private static final int MAX_RETRY_COUNT = 3;

    private final StorageService storageService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void execute() {
        outboxRepository.findByStatus(OutboxStatus.PENDING)
                .forEach(this::process);
    }

    private void process(Outbox outbox) {
        if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
            outbox.markDead();
            outboxRepository.save(outbox);
            log.warn("Outbox 최대 재시도 초과 - outboxId: {}", outbox.getId());
            return;
        }

        try {
            PurgePersonalPayload payload = objectMapper.readValue(outbox.getPayload(), PurgePersonalPayload.class);
            storageService.delete(payload.imageUrl());
            outbox.markDone();
            outboxRepository.save(outbox);

        } catch (Exception e) {
            outbox.incrementRetryCount();
            outboxRepository.save(outbox);
            log.warn("Outbox 재시도 처리 실패 - outboxId: {}, retryCount: {}", outbox.getId(), outbox.getRetryCount(), e);
        }
    }
}
