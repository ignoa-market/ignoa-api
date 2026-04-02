package io.wisoft.ignoa_api.product.controller;

import io.wisoft.ignoa_api.product.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@RestController
@RequestMapping("/api/products/{productId}")
@RequiredArgsConstructor
public class ProductSseController {

    private final SseEmitterManager sseEmitterManager;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long productId) {
        return sseEmitterManager.subscribe(productId);
    }
}
