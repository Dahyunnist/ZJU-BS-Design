package com.zju.imagemanger.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at"))
@Data
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion = 0L;
    @Column(nullable = false, length = 64)
    private String type;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(nullable = false)
    private int attempts;
}
