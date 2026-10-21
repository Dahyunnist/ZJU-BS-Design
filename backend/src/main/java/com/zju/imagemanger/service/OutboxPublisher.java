package com.zju.imagemanger.service;

import com.zju.imagemanger.config.QueueConfiguration;
import com.zju.imagemanger.entity.OutboxEvent;
import com.zju.imagemanger.repository.OutboxEventRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxClaimService claimService;

    public OutboxPublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate, OutboxClaimService claimService) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.claimService = claimService;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> batch = claimService.claimBatch();
        for (OutboxEvent event : batch) {
            try {
                CorrelationData correlation = new CorrelationData("outbox-" + event.getId());
                rabbitTemplate.convertAndSend(QueueConfiguration.PROCESSING_EXCHANGE, QueueConfiguration.ROUTING_KEY,
                        new ImageCreatedMessage(event.getId(), event.getAggregateId(), event.getAggregateVersion()), correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ did not route and confirm outbox event");
                }
                event.setStatus("SENT");
                event.setSentAt(java.time.LocalDateTime.now());
                event.setLeaseUntil(null);
                logger.info("Published outbox eventId={} imageId={}", event.getId(), event.getAggregateId());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                scheduleRetry(event);
                repository.save(event);
                return;
            } catch (Exception error) {
                logger.warn("Outbox publish failed eventId={} imageId={}", event.getId(), event.getAggregateId(), error);
                scheduleRetry(event);
            }
            repository.save(event);
        }
    }

    private void scheduleRetry(OutboxEvent event) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setStatus("PENDING");
        event.setLeaseUntil(null);
        long delaySeconds = Math.min(30, 1L << Math.min(attempts, 8));
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
    }

    public record ImageCreatedMessage(Long eventId, Long imageId, Long aggregateVersion) implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }
}
