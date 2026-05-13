package io.wisoft.ignoa_api.global.outbox.service;

import io.wisoft.ignoa_api.global.outbox.entity.Outbox;
import io.wisoft.ignoa_api.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxRepository outboxRepository;

    public void save(Long userId, String profileImageUrl) {
        Outbox outbox = Outbox.create(userId.toString(), "USER", "PURGE_PERSONAL_DATA",
                """
                        {
                          "userId": %d,
                          "imageUrl": "%s"
                        }
                        """.formatted(userId, profileImageUrl));

        outboxRepository.save(outbox);
        log.info("Outbox 저장 완료 - userId: {}", userId);
    }
}
