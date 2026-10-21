package com.zju.imagemanger.service;

import com.zju.imagemanger.entity.OutboxEvent;
import com.zju.imagemanger.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxClaimService {
    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<OutboxEvent> claimBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> batch = repository.claimable(now);
        for (OutboxEvent event : batch) {
            event.setStatus("PUBLISHING");
            event.setLeaseUntil(now.plusSeconds(30));
        }
        return batch;
    }
}
