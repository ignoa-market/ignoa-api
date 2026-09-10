package io.wisoft.ignoa_api.global.outbox.entity;

import io.wisoft.ignoa_api.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String aggregateId;

    @Column(nullable = false, updatable = false)
    private String aggregateType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutboxEventType eventType;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    private int retryCount;

    private LocalDateTime processedAt;

    private Outbox(String aggregateId, String aggregateType, OutboxEventType eventType, String payload) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static Outbox create(String aggregateId, String aggregateType, OutboxEventType eventType, String payload) {
        return new Outbox(aggregateId, aggregateType, eventType, payload);
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.processedAt = LocalDateTime.now();
    }

    public void markDead() {
        this.status = OutboxStatus.DEAD;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}

