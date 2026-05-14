package io.wisoft.ignoa_api.global.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wisoft.ignoa_api.global.outbox.dto.PurgePersonalPayload;
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
    private final ObjectMapper objectMapper;

    public void save(Long userId, String profileImageUrl) {
        try {
            String payload = objectMapper.writeValueAsString(new PurgePersonalPayload(userId, profileImageUrl));
            Outbox outbox = Outbox.create(userId.toString(), "USER", "PURGE_PERSONAL_DATA", payload);

            outboxRepository.save(outbox);
            log.info("Outbox 저장 완료 - userId: {}", userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
