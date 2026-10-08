package io.wisoft.ignoa_api.global.outbox.service;

import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxStatus;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxJob {

    private static final int MAX_RETRY_COUNT = 3;

    private final StorageService storageService;
    private final OutboxRepository outboxRepository;

    public void execute() {
        long startedAt = System.nanoTime();
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

        if (!outboxList.isEmpty()) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            log.info(
                    "Outbox 처리 결과: target={}, completed={}, retryPending={}, dead={}, durationMs={}",
                    outboxList.size(),
                    completedCount,
                    retryPendingCount,
                    deadCount,
                    durationMs
            );
        }
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

            log.debug(
                    "Outbox 처리 완료: outboxId={}, eventType={}",
                    outbox.getId(),
                    outbox.getEventType()
            );

            return ProcessResult.COMPLETED;

        } catch (Exception e) {
            outbox.incrementRetryCount();

            // 이번 실패로 재시도 한도에 도달한 경우
            if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                outbox.markDead();
                outboxRepository.save(outbox);

                log.error(
                        "Outbox 처리 중단: outboxId={}, eventType={}, retryCount={}, reason=최대 재시도 초과, action=수동 확인",
                        outbox.getId(),
                        outbox.getEventType(),
                        outbox.getRetryCount(),
                        e
                );

                return ProcessResult.DEAD;
            }

            // 재시도 가능한 경우
            outboxRepository.save(outbox);

            log.debug(
                    "Outbox 재시도 대기: outboxId={}, eventType={}, retryCount={}, reason={}",
                    outbox.getId(),
                    outbox.getEventType(),
                    outbox.getRetryCount(),
                    e.getClass().getSimpleName()
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
