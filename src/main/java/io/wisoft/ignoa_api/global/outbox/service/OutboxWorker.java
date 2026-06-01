package io.wisoft.ignoa_api.global.outbox.service;

import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxStatus;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private static final int MAX_RETRY_COUNT = 3;

    private final StorageService storageService;
    private final OutboxRepository outboxRepository;

    public void execute() {
        List<Outbox> outboxList = outboxRepository.findByStatus(OutboxStatus.PENDING);
        log.info("Outbox 처리 시작 - 대상: {}건", outboxList.size());
        outboxList.forEach(this::process);
    }

    private void process(Outbox outbox) {
        if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
            outbox.markDead();
            outboxRepository.save(outbox);
            log.warn("Outbox 최대 재시도 초과 - outboxId: {}, eventType: {}", outbox.getId(), outbox.getEventType());
            return;
        }

        try {
            storageService.delete(outbox.getPayload());
            outbox.markDone();
            outboxRepository.save(outbox);
            log.info("Outbox 처리 완료 - outboxId: {}, eventType: {}", outbox.getId(), outbox.getEventType());

        } catch (Exception e) {
            outbox.incrementRetryCount();
            outboxRepository.save(outbox);
            log.warn("Outbox 재시도 처리 실패 - outboxId: {}, eventType: {}, retryCount: {}", outbox.getId(), outbox.getEventType(), outbox.getRetryCount(), e);
        }
    }
}
