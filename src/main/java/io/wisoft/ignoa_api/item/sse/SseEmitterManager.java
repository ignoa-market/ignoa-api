package io.wisoft.ignoa_api.item.sse;

import io.wisoft.ignoa_api.bid.dto.response.BidBroadcast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitterManager {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long itemId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(itemId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(itemId, emitter));
        emitter.onTimeout(() -> remove(itemId, emitter));
        emitter.onError(e -> remove(itemId, emitter));

        return emitter;
    }

    public void send(Long itemId, BidBroadcast bidBroadcast) {
        List<SseEmitter> itemEmitters = emitters.getOrDefault(itemId, List.of());

        for (SseEmitter emitter : itemEmitters) {
            try {
                emitter.send(SseEmitter.event().data(bidBroadcast));
            } catch (Exception e) {
                log.error("SSE 전송 실패 itemId={}", itemId, e);
                remove(itemId, emitter);
            }
        }
    }

    private void remove(Long itemId, SseEmitter emitter) {
        List<SseEmitter> itemEmitters = emitters.get(itemId);

        if (itemEmitters != null) {
            itemEmitters.remove(emitter);
        }
    }
}
