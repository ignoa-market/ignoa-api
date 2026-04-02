package io.wisoft.ignoa_api.product.sse;

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

    public SseEmitter subscribe(Long productId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(productId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(productId, emitter));
        emitter.onTimeout(() -> remove(productId, emitter));
        emitter.onError(e -> remove(productId, emitter));

        return emitter;
    }

    public void send(Long productId, BidBroadcast bidBroadcast) {
        List<SseEmitter> productEmitters = emitters.getOrDefault(productId, List.of());

        for (SseEmitter emitter : productEmitters) {
            try {
                emitter.send(SseEmitter.event().data(bidBroadcast));
            } catch (Exception e) {
                log.error("SSE 전송 실패 productId={}", productId, e);
                remove(productId, emitter);
            }
        }
    }

    private void remove(Long productId, SseEmitter emitter) {
        List<SseEmitter> productEmitters = emitters.get(productId);

        if (productEmitters != null) {
            productEmitters.remove(emitter);
        }
    }
}
