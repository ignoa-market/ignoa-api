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

        int completedCount = 0;
        int retryPendingCount = 0;
        int deadCount = 0;

        for (Outbox outbox : outboxList) {
            ProcessResult result = process(outbox);

            switch (result) {
                case COMPLETED -> completedCount++;
                case RETRY_PENDING -> retryPendingCount++;
                case DEAD -> deadCount++;
            }
        }

        log.info(
                "Outbox 처리 작업 완료: target={}, completed={}, retryPending={}, dead={}",
                outboxList.size(),
                completedCount,
                retryPendingCount,
                deadCount
        );
    }

    private ProcessResult process(Outbox outbox) {
        if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
            outbox.markDead();
            outboxRepository.save(outbox);
            log.error(
                    "Outbox 처리 중단: outboxId={}, eventType={}, reason=최대 재시도 초과",
                    outbox.getId(),
                    outbox.getEventType()
            );
            return ProcessResult.DEAD;
        }

        try {
            storageService.delete(outbox.getPayload());
            outbox.markDone();
            outboxRepository.save(outbox);
            log.debug("Outbox 처리 완료: outboxId={}, eventType={}", outbox.getId(), outbox.getEventType());
            return ProcessResult.COMPLETED;

        } catch (Exception e) {
            outbox.incrementRetryCount();
            outboxRepository.save(outbox);
            log.warn(
                    "Outbox 처리 실패: outboxId={}, eventType={}, retryCount={}, action=재시도 대기",
                    outbox.getId(),
                    outbox.getEventType(),
                    outbox.getRetryCount(),
                    e
            );
            return ProcessResult.RETRY_PENDING;
        }
    }

    private enum ProcessResult {
        COMPLETED,
        RETRY_PENDING,
        DEAD
    }
}
